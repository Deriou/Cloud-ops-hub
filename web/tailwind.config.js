/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,ts}"],
  theme: {
    extend: {
      colors: {
        panel: "rgba(255, 255, 255, 0.82)",
        "panel-soft": "rgba(247, 250, 255, 0.72)",
        border: "rgba(148, 189, 220, 0.34)",
        ink: "#16324f",
        "ink-soft": "#5a6f88",
        accent: "#0ea5e9",
        success: "#0f9f6e",
        warning: "#c0821d",
        danger: "#dc5d69"
      },
      boxShadow: {
        soft: "0 24px 70px rgba(56, 118, 165, 0.14)",
        float: "0 18px 45px rgba(104, 162, 208, 0.14)"
      }
    }
  },
  plugins: []
};
