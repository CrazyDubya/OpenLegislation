module.exports = {
  content: [
    './WEB-INF/app/**/*.js',
    './WEB-INF/app/**/*.html',
  ],
  darkMode: false, // or 'media' or 'class'
  theme: {
    extend: {
      colors: {
        blue: {
          100: '#c4e7f8',
          200: '#97cee7',
          300: '#4bafdd',
          400: '#2694C7',
          500: '#008CBA',
          600: '#1F77A1',
          700: '#2B6A90',
          800: '#165B81',
          900: '#0e3a4e',
        },
        gray: {
          450: '#848B98',
        }
      },
      borderWidth: {
        '1': '1px'
      },
    },
  },
  safelist: [
    // Keep commonly used utility classes
    'transform',
    'translate-y-0.5',
    'hover:translate-y-0.5',
    'shadow-md',
    'disabled:shadow-none',
    'disabled:cursor-default',
    'disabled:opacity-70',
    'disabled:bg-blue-700',
  ],
  plugins: [],
}
