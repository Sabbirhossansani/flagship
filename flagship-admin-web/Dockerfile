FROM nginx:alpine
COPY flagship-admin-web/src/main/resources/static /usr/share/nginx/html
COPY flagship-admin-web/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
