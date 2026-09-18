<template>
    <el-card class="push-video-page" shadow="never">
        <Video :video-list="videoList" :hide-close="true" v-if="videoList.length > 0" :next-video="nextVideo" />
    </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue';
import { apiVideoByPush } from '../../apis/video.js';
import Video from '../../components/video/index.vue';
const videoList = ref([])
const nextVideo = (idnex) =>{
    if((videoList.value.length - idnex) <= 3) {
        getVideo()
    }
}
const getVideo = ()=>{
    apiVideoByPush().then(({ data }) => {
        if (!data.success) {
            return;
        }
        videoList.value = videoList.value.concat(data.data)
    })
}
onMounted(() => {
    getVideo()
})
</script>
