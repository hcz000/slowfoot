# SlowFoot Frontend

SlowFoot Frontend 是基于 Vue 3 和 Vite 的内容社区 Web 应用，负责登录注册、首页推荐、视频播放、关注 Feed、作品发布、收藏夹、浏览历史、用户中心和后台管理页面。

## 技术栈

- Vue 3
- Vite 4
- Vue Router
- Pinia
- Element Plus
- Axios
- Video.js
- Sass

## 环境要求

- Node.js 18+
- npm 9+
- 可访问 SlowFoot 后端接口

## 安装和启动

```bash
npm install
npm run dev
```

Vite 启动后会输出本地访问地址。生产构建：

```bash
npm run build
npm run preview
```

## 目录结构

```text
src/
├── apis/          Axios 请求和后端接口封装
├── components/    通用组件
├── views/         页面视图
├── router/        路由配置
├── stores/        Pinia 状态
├── plugins/       UI 和插件配置
└── main.js        应用入口
```

静态资源和后端一起发布时，后端 `src/main/resources/static` 也包含一份构建后的资源；日常开发优先使用本目录的 Vite 开发服务器。

## 后端连接

接口请求统一通过 `src/apis/request.js` 处理。后端当前接口前缀为 `/luckyjourney`，默认服务端口为 `8882`。如需切换开发环境，请在请求封装或 Vite 代理配置中修改，不要把线上地址写死到业务组件。

## 开发约定

- 页面业务逻辑放在 `views`，跨页面复用状态放入 Pinia。
- 后端调用放在 `apis`，组件不要直接拼接 Axios 请求。
- 文件上传使用后端签发的 OSS 临时凭证或资源地址，不在前端保存 OSS 密钥。
- 视频播放、上传、列表分页和空状态需要同时考虑加载中、失败和无数据状态。
- 修改接口字段时同步检查后端 DTO、API 封装和页面展示。

## 常用命令

```bash
npm run dev       # 本地开发
npm run build     # 生产构建
npm run preview   # 预览生产构建
```

## 相关文档

- [SlowFoot 项目总览](../README.md)
- [架构设计文档](../docs/架构设计文档.md)
- [贡献指南](../CONTRIBUTING.md)
