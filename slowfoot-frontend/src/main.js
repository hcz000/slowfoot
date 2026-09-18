import { createPinia } from "pinia";
import videojs from "video.js";
import "video.js/dist/video-js.css";
import { createApp } from 'vue';
import App from './App.vue';
import ElementPlus from './plugins/element-plus';
import router from './router';

// 创建应用
const luckJourneyApp = createApp(App)
luckJourneyApp.use(createPinia())
luckJourneyApp.use(router)
luckJourneyApp.use(ElementPlus)
luckJourneyApp.config.globalProperties.$video = videojs;
luckJourneyApp.mount('#app')

