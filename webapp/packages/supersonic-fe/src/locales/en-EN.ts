import component from './en-EN/component';
import globalHeader from './en-EN/globalHeader';
import menu from './en-EN/menu';
import pwa from './en-EN/pwa';
import settingDrawer from './en-EN/settingDrawer';
import settings from './en-EN/settings';
import pages from './en-EN/pages';

export default {
'navBar.lang': 'Language',
  'layout.user.link.help': 'Help',
  'layout.user.link.privacy': 'Privacy',
  'layout.user.link.terms': 'Terms',
  'app.preview.down.block': 'Download this page to your local project',
  'app.welcome.link.fetch-blocks': 'Fetch all blocks',
  'app.welcome.link.block-list': 'Develop standard pages quickly with blocks',
  ...pages,
  ...globalHeader,
  ...menu,
  ...settingDrawer,
  ...settings,
  ...pwa,
  ...component,
};
