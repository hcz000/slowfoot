import { createRouter, createWebHashHistory } from "vue-router";
import Home from "../views/home/index.vue";
import Channel from "../views/channel/index.vue";

const routes = [
  {
    path: "/",
    component: Home,
  },
  {
    path: "/video/:classify",
    component: Channel,
    meta: {
      isClassify: true
    }
  }, {
    path: "/video/search/:key",
    component: Home,
    meta: {
      isSearch: true
    }
  },
  {
    path: "/followVideo",
    component: () => import("../views/followVideo/index.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/video",
    redirect: "/",
  },
  {
    path: "/pushVideo",
    component: () => import("../views/pushVideo/index.vue"),
  },
  {
    path: "/notifications",
    component: () => import("../views/notification/index.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/user",
    component: () => import("../views/user/index.vue"),
    redirect: "/user/home",
    children: [
      {
        path: "home",
        component: () => import("../views/user/home/index.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "video",
        redirect: "/user/publish",
      },
      {
        path: "publish",
        component: () => import("../views/user/myVideo/publish.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "favorites",
        component: () => import("../views/user/favorites/index.vue"),
        meta: { requiresAuth: true },
      },
      {
        path: "like",
        component: () => import("../views/user/like/index.vue"),
        meta: { requiresAuth: true }
      },
      {
        path: "history",
        component: () => import("../views/user/history/index.vue"),
        meta: { requiresAuth: true }
      }
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes: routes,
});

router.beforeEach((to) => {
  if (to.matched.some((record) => record.meta.requiresAuth) && !sessionStorage.getItem('token')) {
    return { path: '/', query: { login: 'required' } }
  }
})

export default router;
