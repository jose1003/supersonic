package com.tencent.supersonic.chat.server.executor;

import com.tencent.supersonic.chat.api.pojo.enums.MemoryStatus;
import com.tencent.supersonic.chat.server.persistence.dataobject.ChatMemoryDO;
import com.tencent.supersonic.chat.server.pojo.ExecuteContext;
import com.tencent.supersonic.chat.server.service.MemoryService;
import com.tencent.supersonic.chat.server.util.ResultFormatter;
import com.tencent.supersonic.common.pojo.QueryColumn;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.headless.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.headless.api.pojo.request.QuerySqlReq;
import com.tencent.supersonic.headless.api.pojo.response.QueryResult;
import com.tencent.supersonic.headless.api.pojo.response.QueryState;
import com.tencent.supersonic.headless.api.pojo.response.SemanticQueryResp;
import com.tencent.supersonic.chat.server.pojo.ChatContext;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMSqlQuery;
import com.tencent.supersonic.headless.server.facade.service.SemanticLayerService;
import com.tencent.supersonic.chat.server.service.ChatContextService;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SqlExecutor implements ChatExecutor {

    @SneakyThrows
    @Override
    public QueryResult execute(ExecuteContext executeContext) {
        QueryResult queryResult = doExecute(executeContext);

        if (queryResult != null) {
            String textResult = ResultFormatter.transform2TextNew(queryResult.getQueryColumns(),
                    queryResult.getQueryResults());
            queryResult.setTextResult(textResult);

            if (queryResult.getQueryState().equals(QueryState.SUCCESS)
                    && queryResult.getQueryMode().equals(LLMSqlQuery.QUERY_MODE)) {
                MemoryService memoryService = ContextUtils.getBean(MemoryService.class);
                memoryService.createMemory(ChatMemoryDO.builder()
                        .agentId(executeContext.getAgentId())
                        .status(MemoryStatus.PENDING)
                        .question(executeContext.getQueryText())
                        .s2sql(executeContext.getParseInfo().getSqlInfo().getParsedS2SQL())
                        .dbSchema(buildSchemaStr(executeContext.getParseInfo()))
                        .createdBy(executeContext.getUser().getName())
                        .updatedBy(executeContext.getUser().getName())
                        .createdAt(new Date())
                        .build());
            }
        }

        return queryResult;
    }

    @SneakyThrows
    private QueryResult doExecute(ExecuteContext executeContext) {
        SemanticLayerService semanticLayer = ContextUtils.getBean(SemanticLayerService.class);
        ChatContextService chatContextService = ContextUtils.getBean(ChatContextService.class);

        ChatContext chatCtx = chatContextService.getOrCreateContext(executeContext.getChatId());
        SemanticParseInfo parseInfo = executeContext.getParseInfo();
        if (Objects.isNull(parseInfo.getSqlInfo())
                || StringUtils.isBlank(parseInfo.getSqlInfo().getCorrectedS2SQL())) {
            return null;
        }

        QuerySqlReq sqlReq = QuerySqlReq.builder()
                .sql(parseInfo.getSqlInfo().getCorrectedS2SQL())
                .build();
        sqlReq.setSqlInfo(parseInfo.getSqlInfo());
        sqlReq.setDataSetId(parseInfo.getDataSetId());
        long startTime = System.currentTimeMillis();
        SemanticQueryResp queryResp = semanticLayer.queryByReq(sqlReq, executeContext.getUser());
        QueryResult queryResult = new QueryResult();
        queryResult.setParseInfo(parseInfo);
        queryResult.setQueryMode(parseInfo.getQueryMode());
        if (queryResp != null) {
            queryResult.setQueryAuthorization(queryResp.getQueryAuthorization());
            List<Map<String, Object>> resultList = queryResp == null ? new ArrayList<>()
                    : queryResp.getResultList();
            List<QueryColumn> columns = queryResp == null ? new ArrayList<>() : queryResp.getColumns();
            queryResult.setQueryTimeCost(System.currentTimeMillis() - startTime);
            queryResult.setQuerySql(queryResp.getSql());
            queryResult.setQueryResults(resultList);
            queryResult.setQueryColumns(columns);
            queryResult.setQueryState(QueryState.SUCCESS);

            chatCtx.setParseInfo(parseInfo);
            chatContextService.updateContext(chatCtx);
        } else {
            queryResult.setQueryState(QueryState.INVALID);
            queryResult.setQueryMode(parseInfo.getQueryMode());
        }
        return queryResult;
    }

    public String buildSchemaStr(SemanticParseInfo parseInfo) {
        String tableStr = parseInfo.getDataSet().getName();
        StringBuilder metricStr = new StringBuilder();
        StringBuilder dimensionStr = new StringBuilder();

        parseInfo.getMetrics().stream().forEach(
                metric -> {
                    metricStr.append(metric.getName());
                    if (StringUtils.isNotEmpty(metric.getDescription())) {
                        metricStr.append(" COMMENT '" + metric.getDescription() + "'");
                    }
                    if (StringUtils.isNotEmpty(metric.getDefaultAgg())) {
                        metricStr.append(" AGGREGATE '" + metric.getDefaultAgg().toUpperCase() + "'");
                    }
                    metricStr.append(",");
                }
        );

        parseInfo.getDimensions().stream().forEach(
                dimension -> {
                    dimensionStr.append(dimension.getName());
                    if (StringUtils.isNotEmpty(dimension.getDescription())) {
                        dimensionStr.append(" COMMENT '" + dimension.getDescription() + "'");
                    }
                    dimensionStr.append(",");
                }
        );

        String template = "Table: %s, Metrics: [%s], Dimensions: [%s]";
        return String.format(template, tableStr, metricStr, dimensionStr);
    }

}
