
# CampusTrade Frontend

CampusTrade 是一个面向校园生活的二手交易平台。本项目采用前后端分离架构，前端负责页面展示、用户交互和 API 请求，通过 REST API 与 Spring Boot 后端进行通信。

## 技术栈

- Vue 3
- Vite
- TypeScript
- Node.js
- npm

## 项目结构

```text
frontend/
├── public/              # 静态资源
├── src/
│   ├── assets/          # 图片及其他资源
│   ├── components/      # 可复用组件
│   ├── views/           # 页面组件
│   ├── App.vue          # 根组件
│   ├── main.ts          # 应用入口
│   └── style.css        # 全局样式
├── index.html
├── package.json
├── package-lock.json
└── vite.config.ts
```

## 环境要求

- Node.js 24
- npm 11

## 本地开发

进入 frontend 目录：

```bash
cd frontend
```

安装项目依赖：

```bash
npm install
```

启动开发服务器：

```bash
npm run dev
```

浏览器访问终端输出的本地地址，通常为：

```text
http://localhost:5173/
```

## 生产构建

执行：

```bash
npm run build
```

构建完成后，生产文件将生成在 `dist/` 目录下。

本地预览生产构建结果：

```bash
npm run preview
```

## 开发说明

前端与后端位于同一个 Git 仓库中，分别维护。

- 前端：Vue 3 + TypeScript
- 后端：Spring Boot + MyBatis + MySQL
- 通信方式：REST API
- 用户认证：Spring Security + Session

当前前端处于基础工程搭建阶段，后续将逐步实现用户认证、商品浏览、商品发布及交易相关页面。
