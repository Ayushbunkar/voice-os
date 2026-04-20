FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci

# Copy source code and build
COPY . .
RUN npm run build

# Production image
FROM node:20-alpine

WORKDIR /app

# Only copy production dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy compiled output from builder
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/database ./database

# Expose API port
EXPOSE 5000

# Set Node ENV
ENV NODE_ENV=production

# Command to run the application
CMD ["npm", "start"]
