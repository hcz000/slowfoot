<template>
    <el-card class="follow-page" shadow="never">
        <Video :next-video="nextVideo" :hide-close="true" :video-list="videoList" v-if="videoList.length > 0" />
        <el-card v-else class="follow-empty-card" shadow="never">
            <div class="py-12 text-center">
                <i class="mdi mdi-eye follow-empty-icon" />

                <div class="text-h4 font-weight-bold">您关注的人没有视频哦~</div>
            </div>

            <el-divider></el-divider>

            <div class="pa-4 text-end">
                <el-button class="text-none" type="info" plain @click="$router.push('/')">
                    去看推荐
                </el-button>
            </div>
        </el-card>
    </el-card>
</template>
<script setup>
import { onMounted, ref } from 'vue';
import { apiGetFollowVideo } from '../../apis/video.js';
import Video from '../../components/video/index.vue';
const videoList = ref([])

const nextVideo = (idnex) =>{
    if((videoList.value.length - idnex) <= 3) {
        getVideo()
    }
}
const getVideo =()=>{
    let time = Date.now()
    if(videoList.value.length>0) {
        time = new Date(videoList.value[videoList.value.length-1].gmtCreated).getTime()
    }
    apiGetFollowVideo(time).then(({ data }) => {
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
