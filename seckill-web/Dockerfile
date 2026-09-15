# 秒杀前端：静态页面托管 + /api 反向代理到网关（同源，免 CORS）
FROM nginx:alpine

# 站点配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 静态资源（C 端 index.html / 运营端 admin.html）
COPY public /usr/share/nginx/html
