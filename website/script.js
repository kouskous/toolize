document.querySelectorAll(".copy-btn").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const target = document.getElementById(btn.dataset.copyTarget);
    if (!target) return;

    try {
      await navigator.clipboard.writeText(target.textContent.trim());
      const original = btn.textContent;
      btn.textContent = "Copié !";
      setTimeout(() => {
        btn.textContent = original;
      }, 1500);
    } catch (err) {
      console.error("Copy failed", err);
    }
  });
});
