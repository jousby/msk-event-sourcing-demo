# base image
FROM node:13.8.0
WORKDIR /app
COPY package.json /app/package.json
RUN npm install --silent
COPY ./ /app/
CMD ["npm", "start"]