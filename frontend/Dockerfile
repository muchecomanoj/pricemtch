FROM node:23

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

RUN npm install -g serve

EXPOSE 5174

CMD ["serve", "-s", "dist", "-p", "5174"]