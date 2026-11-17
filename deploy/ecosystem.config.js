// PM2 process file:  pm2 start ecosystem.config.js && pm2 save && pm2 startup
module.exports = {
  apps: [{
    name: 'shop-api',
    cwd: '/var/www/shop/backend',
    script: 'src/server.js',
    instances: 1,            // the stock-reservation sweeper should run in a single process
    env: { NODE_ENV: 'production' },
    max_memory_restart: '400M',
    out_file: '/var/log/shop/api-out.log',
    error_file: '/var/log/shop/api-error.log',
    time: true,
  }],
};
