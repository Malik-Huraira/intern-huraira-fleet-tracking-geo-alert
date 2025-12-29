# Use Node 20 LTS
FROM node:20-bullseye-slim

# Set working directory
WORKDIR /app

# Create tmp directory for Vite cache
RUN mkdir -p /tmp/.vite && chmod 777 /tmp/.vite

# Copy package.json and package-lock.json first
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy remaining source files
COPY . .

# Expose port
EXPOSE 8081

# Start dev server
CMD ["npm", "run", "dev"]
