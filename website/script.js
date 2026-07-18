document.querySelectorAll(".copy-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const target = document.getElementById(btn.dataset.copyTarget);
    if (!target) return;

    try {
      await navigator.clipboard.writeText(target.textContent.trim());
      const original = btn.textContent;
      btn.textContent = "copied!";
      setTimeout(() => {
        btn.textContent = original;
      }, 1500);
    } catch (err) {
      console.error("Copy failed", err);
    }
  });
});

const typedEl = document.getElementById("typed");
const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const typedText = "docker run toolize";

if (typedEl) {
  if (prefersReducedMotion) {
    typedEl.textContent = typedText;
  } else {
    let i = 0;
    const type = () => {
      typedEl.textContent = typedText.slice(0, i);
      i++;
      if (i <= typedText.length) {
        setTimeout(type, 65);
      }
    };
    type();
  }
}
