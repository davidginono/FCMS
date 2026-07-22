document.addEventListener("DOMContentLoaded", () => {
  const menuButton = document.querySelector("[data-fcms-menu]");
  const sidebar = document.querySelector("[data-fcms-sidebar]");

  if (menuButton && sidebar) {
    menuButton.addEventListener("click", () => {
      sidebar.classList.toggle("hidden");
    });
  }
});
