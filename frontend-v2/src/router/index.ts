import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import PlatformLayout from '../layouts/PlatformLayout.vue'
import WorkbenchView from '../views/workbench/WorkbenchView.vue'
import LoginView from '../views/auth/LoginView.vue'
import { authApi } from '../api/auth'
const routes:RouteRecordRaw[]=[{path:'/login',name:'login',component:LoginView,meta:{public:true}},{path:'/',component:PlatformLayout,children:[
{path:'',name:'workbench',component:WorkbenchView,meta:{module:'workbench'}},
{path:'integration/overview',component:()=>import('../views/integration/IntegrationOverview.vue'),meta:{module:'integration'}},
{path:'integration/datasources',component:()=>import('../views/integration/DataSourceList.vue'),meta:{module:'integration'}},
{path:'integration/batch',component:()=>import('../views/integration/BatchSyncList.vue'),meta:{module:'integration'}},
{path:'integration/realtime',component:()=>import('../views/integration/RealtimeSyncList.vue'),meta:{module:'integration'}},
{path:'integration/instances',component:()=>import('../views/integration/IntegrationInstances.vue'),meta:{module:'integration'}},
{path:'development/workspace',component:()=>import('../views/development/DevelopmentWorkspace.vue'),meta:{module:'development'}},
{path:'development/versions',component:()=>import('../views/development/DevelopmentVersions.vue'),meta:{module:'development'}},
{path:'workflow/definitions',component:()=>import('../views/workflow/WorkflowDefinitions.vue'),meta:{module:'workflow'}},
{path:'operations/overview',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
{path:'operations/instances',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
{path:'operations/failures',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
{path:'operations/alerts',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
{path:'metadata/catalog',component:()=>import('../views/metadata/MetadataCatalog.vue'),meta:{module:'metadata'}},
{path:'metrics/overview',component:()=>import('../views/metrics/MetricsCenter.vue'),meta:{module:'metrics'}},
{path:'metrics/manage',component:()=>import('../views/metrics/MetricsCenter.vue'),meta:{module:'metrics'}},
{path:'metrics/dimensions',component:()=>import('../views/metrics/MetricsCenter.vue'),meta:{module:'metrics'}},
{path:'metrics/lineage',component:()=>import('../views/metrics/MetricsCenter.vue'),meta:{module:'metrics'}},
{path:'assets/catalog',component:()=>import('../views/assets/AssetCatalog.vue'),meta:{module:'assets'}},
{path:'assets/favorites',component:()=>import('../views/assets/AssetCatalog.vue'),meta:{module:'assets'}},
{path:'release/history',component:()=>import('../views/release/ReleaseCenter.vue'),meta:{module:'release'}},
{path:'release/queue',component:()=>import('../views/release/ReleaseCenter.vue'),meta:{module:'release'}},
{path:'release/policy',component:()=>import('../views/release/ReleaseCenter.vue'),meta:{module:'release'}},
{path:'system/users',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}},
{path:'system/roles',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}},
{path:'system/environments',component:()=>import('../views/system/RuntimeEnvironments.vue'),meta:{module:'system'}},
{path:'system/audit',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}}
]}]
const router=createRouter({history:createWebHistory(),routes})
router.beforeEach(async(to)=>{
  if(to.meta.public)return true
  try{
    const me=await authApi.me()
    if(me.authenticated)return true
  }catch{}
  return {path:'/login',query:{redirect:to.fullPath}}
})
export default router
