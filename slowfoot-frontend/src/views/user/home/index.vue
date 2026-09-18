<template>
    <el-card shadow="never">
        <el-divider/>
        <VideoListVue :video-list="videoList"/>
        <el-pagination
        v-if="pageInfo.pages>1"
              v-model:current-page="pageInfo.page"
              :page-count="pageInfo.pages"
            />
    </el-card>
</template>
<script setup>
import { ref, watch } from 'vue';
import { apiGetUserVideoById } from '../../../apis/video';
import VideoListVue from '../../../components/video/list.vue';
import { useUserStore } from '../../../stores';
const userStore = useUserStore()
const videoList = ref([])
const pageInfo = ref({
    page: 1,
    pages: 0
})
const getVideo = ()=>{
    apiGetUserVideoById(userStore.$state.lookId, pageInfo.value.page).then(({data})=>{
        if(!data.success){
            return
        }
        pageInfo.value.pages = data.data.pages
        videoList.value = data.data.records
    })
}
watch(()=>pageInfo.value.page, ()=>{
    getVideo()
}, {immediate:true})
</script>
