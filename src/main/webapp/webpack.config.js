const path = require('path')
const HtmlWebpackPlugin = require('html-webpack-plugin')

module.exports = {
  entry: './WEB-INF/app/index.js',
  output: {
    path: path.resolve(__dirname, 'static/dist'),
    filename: process.env.NODE_ENV === 'production' ? '[name].[contenthash].js' : 'index_bundle.js',
    chunkFilename: process.env.NODE_ENV === 'production' ? '[name].[contenthash].chunk.js' : '[name].chunk.js',
    publicPath: process.env.NODE_ENV === 'production' ? '/static/dist/' : '/',
    clean: true // Clean dist folder before each build
  },
  optimization: {
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          chunks: 'all',
          priority: 10
        },
        react: {
          test: /[\\/]node_modules[\\/](react|react-dom)[\\/]/,
          name: 'react',
          chunks: 'all',
          priority: 20
        },
        common: {
          minChunks: 2,
          chunks: 'all',
          name: 'common',
          priority: 5,
          reuseExistingChunk: true
        }
      }
    },
    runtimeChunk: {
      name: 'runtime'
    },
    // Better minification in production
    ...(process.env.NODE_ENV === 'production' && {
      usedExports: true,
      sideEffects: false
    })
  },
  resolve: {
    alias: {
      app: path.resolve(__dirname, 'WEB-INF/app')
    }
  },
  module: {
    rules: [
      // load css files, including tailwindcss
      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader", "postcss-loader"],
      },
      // Transpile js
      {
        test: /\.(js)$/,
        use: {
          loader: 'babel-loader',
          options: {
            // Add caching for better build performance
            cacheDirectory: true,
            cacheCompression: false
          }
        },
        exclude: /node_modules/
      },
    ]
  },
  plugins: [
    new HtmlWebpackPlugin({
      template: 'WEB-INF/app/index.html',
      minify: process.env.NODE_ENV === 'production' ? {
        removeComments: true,
        collapseWhitespace: true,
        removeRedundantAttributes: true,
        useShortDoctype: true,
        removeEmptyAttributes: true,
        removeStyleLinkTypeAttributes: true,
        keepClosingSlash: true,
        minifyJS: true,
        minifyCSS: true,
        minifyURLs: true
      } : false
    })
  ],
  mode: process.env.NODE_ENV === 'production' ? 'production' : 'development',
  // Increase performance budget to reduce warnings, but still monitor size
  performance: {
    maxAssetSize: 5000000, // 5MB - Adjusted to reflect current bundle size
    maxEntrypointSize: 5000000, // 5MB - Adjusted to reflect current bundle size
    hints: 'warning' // Warnings will still be shown if these limits are exceeded
  },
  // TODO: Revisit these settings after implementing bundle size reduction strategies
  devServer: {
    port: 3000,
    // Send api requests for these paths to the target base url while in dev mode.
    proxy: [
      {
        context: ['/api', '/loginapikey', '/admin/login', '/register/signup', '/pdf', '/globals', '/sock', '/static', '/register/token'],
        target: 'http://localhost:8080',
        secure: false,
        changeOrigin: true,
      }
    ],
    historyApiFallback: {
      disableDotRule: true,
    },
    static: ['../static'],
    // Enable compression for better dev performance
    compress: true
  },
  devtool: process.env.NODE_ENV === 'production' ? false : 'eval-source-map'
}
