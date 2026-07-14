/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts,js}'],
  theme: {
    extend: {
      colors: {
        surface: '#FFFFFF',
        'surface-alt': '#F8FAFC',
        ink: '#111827',
        muted: '#6B7280',
        line: '#E5E7EB',
        accent: {
          DEFAULT: '#6D5CFF',
          hover: '#5B4BEF'
        }
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif']
      },
      maxWidth: {
        content: '1200px'
      }
    }
  },
  plugins: []
}
