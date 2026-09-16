import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import PlatformLayout from '../layouts/PlatformLayout.vue'
import WorkbenchView from '../views/workbench/WorkbenchView.vue'
import LoginView from '../views/auth/LoginView.vue'
import { authApi } from '../api/auth'
import { moduleViewPermission, hasPermission } from '../auth/permissions'
import { productNavigation } from '../navigation'
const routes:RouteRecordRaw[]=[{path:'/login',name:'login',component:LoginView,meta:{public:true}},{path:'/forbidden',name:'forbidden',component:()=>import('../views/auth/ForbiddenView.vue'),meta:{public:true}},{path:'/',component:PlatformLayout,children:[
{path:'',name:'workbench',component:WorkbenchView,meta:{module:'workbench'}},
{path:'integration/overview',component:()=>import('../views/integration/IntegrationOverview.vue'),meta:{module:'integration'}},
{path:'integration/datasources',redirect:'/system/data-sources'},
{path:'integration/batch',component:()=>import('../views/integration/BatchSyncList.vue'),meta:{module:'integration'}},
{path:'integration/realtime',component:()=>import('../views/integration/RealtimeSyncList.vue'),meta:{module:'integration'}},
{path:'integration/instances',component:()=>import('../views/integration/IntegrationInstances.vue'),meta:{module:'integration'}},
{path:'development/workspace',component:()=>import('../views/development/DevelopmentWorkspace.vue'),meta:{module:'development'}},
{path:'development/versions',component:()=>import('../views/development/DevelopmentVersions.vue'),meta:{module:'development'}},
{path:'workflow/definitions',component:()=>import('../views/workflow/WorkflowDefinitions.vue'),meta:{module:'workflow'}},
{path:'operations/overview',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
{path:'operations/tasks',component:()=>import('../views/operations/OperationsCenter.vue'),meta:{module:'operations'}},
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
{path:'system/data-sources',component:()=>import('../views/integration/DataSourceList.vue'),meta:{module:'system'}},
{path:'system/users',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}},
{path:'system/roles',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}},
{path:'system/data-source-permissions',component:()=>import('../views/system/DataSourcePermissions.vue'),meta:{module:'system'}},
{path:'system/environments',component:()=>import('../views/system/RuntimeEnvironments.vue'),meta:{module:'system'}},
{path:'system/alerts',component:()=>import('../views/system/AlertSettings.vue'),meta:{module:'system'}},
{path:'system/audit',component:()=>import('../views/system/SystemAccess.vue'),meta:{module:'system'}}
]}]
const router=createRouter({history:createWebHistory(),routes})
router.beforeEach(async(to)=>{
  if(to.meta.public)return true
  try{
    const me=await authApi.me()
    if(!me.authenticated)return {path:'/login',query:{redirect:to.fullPath}}
    const module=String(to.meta.module||'workbench')
    const required=moduleViewPermission[module]
    if(hasPermission(me,required))return true
    const fallback=productNavigation.find(item=>hasPermission(me,moduleViewPermission[item.key]))
    if(fallback)return {path:fallback.path}
    if(hasPermission(me,moduleViewPermission.release))return {path:'/release/history'}
    if(hasPermission(me,moduleViewPermission.system))return {path:'/system/users'}
    return {path:'/forbidden'}
  }catch{}
  return {path:'/login',query:{redirect:to.fullPath}}
})
export default router
