import { createWebHashHistory, createRouter } from 'vue-router'
import { bookRoutes } from './bookRouter'
import { sourceRoutes } from './sourceRouter'

const router = createRouter({
  //   history: createWebHistory(process.env.BASE_URL),
  history: createWebHashHistory(),
  routes: [
    bookRoutes,
    sourceRoutes,
    {
      path: '/webService',
      name: 'web-service',
      component: () => import('../views/WebServiceSettings.vue'),
    },
    {
      path: '/upload',
      name: 'upload',
      component: () => import('../views/BookUpload.vue'),
    },
    {
      path: '/media',
      name: 'media',
      component: () => import('../views/MediaReader.vue'),
    },
  ].flat(),
})

router.afterEach(to => {
  if (to.name == 'shelf') document.title = 'Kệ sách'
})

export default router
