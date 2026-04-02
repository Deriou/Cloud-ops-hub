/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,ts}"],
  theme: {
    extend: {
      colors: {
        panel: "rgba(15, 23, 42, 0.72)",
        border: "rgba(148, 163, 184, 0.16)"
      },
      boxShadow: {
        soft: "0 16px 50px rgba(15, 23, 42, 0.22)"
      }
    }
  },
  plugins: []
};
