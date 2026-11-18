import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";

const files = [
  ["src/main/resources/static/css/accounting-entry.css", "src/main/webapp/css/accounting-entry.css"],
  ["src/main/resources/static/js/accounting-entry.js", "src/main/webapp/js/accounting-entry.js"],
  ["src/main/resources/static/css/notifications.css", "src/main/webapp/css/notifications.css"],
  ["src/main/resources/static/js/resource-creation.js", "src/main/webapp/js/resource-creation.js"],
  ["src/main/resources/static/js/user-form-session.js", "src/main/webapp/js/user-form-session.js"],
  ["src/main/resources/static/js/user-permissions.js", "src/main/webapp/js/user-permissions.js"],
  ["src/main/resources/static/js/financial-statement-view.js", "src/main/webapp/js/financial-statement-view.js"],
  ["src/main/resources/static/js/password-visibility.js", "src/main/webapp/js/password-visibility.js"],
  ["src/main/resources/static/js/account-form.js", "src/main/webapp/js/account-form.js"],
  ["src/main/resources/static/css/console-shell.css", "src/main/webapp/css/console-shell.css"],
  ["src/main/resources/static/js/shell-navigation.js", "src/main/webapp/js/shell-navigation.js"],
  ["src/main/resources/static/js/workspace-navigation.js", "src/main/webapp/js/workspace-navigation.js"],
  ["src/main/resources/static/js/resource-table.js", "src/main/webapp/js/resource-table.js"],
  ["src/main/resources/static/css/resource-table.css", "src/main/webapp/css/resource-table.css"],
  ["src/main/resources/static/js/transaction-config.js", "src/main/webapp/js/transaction-config.js"],
  ["src/main/resources/static/js/modal-workspace.js", "src/main/webapp/js/modal-workspace.js"],
  ["src/main/resources/static/js/grouped-resource-table.js", "src/main/webapp/js/grouped-resource-table.js"],
  ["src/main/resources/static/css/grouped-resource-table.css", "src/main/webapp/css/grouped-resource-table.css"],
  ["src/main/resources/static/js/live-search.js", "src/main/webapp/js/live-search.js"],
  ["src/main/resources/static/js/choice-loader.js", "src/main/webapp/js/choice-loader.js"],
  ["src/main/resources/static/fonts/open-sans/open-sans-400.woff2", "src/main/webapp/fonts/open-sans/open-sans-400.woff2"],
  ["src/main/resources/static/fonts/open-sans/open-sans-700.woff2", "src/main/webapp/fonts/open-sans/open-sans-700.woff2"],
  ["src/main/resources/static/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-LICENSE.txt", "src/main/webapp/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-LICENSE.txt"],
  ["src/main/resources/static/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-NOTICE.txt", "src/main/webapp/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-NOTICE.txt"],
  ["src/main/resources/static/js/form-ux.js", "src/main/webapp/js/form-ux.js"],
  ["src/main/resources/static/css/workspaces.css", "src/main/webapp/css/workspaces.css"],
  ["src/main/resources/static/css/financial-statements.css", "src/main/webapp/css/financial-statements.css"],
  ["src/main/resources/static/css/financial-statement-print.css", "src/main/webapp/css/financial-statement-print.css"],
  ["src/main/resources/static/css/print-footer.css", "src/main/webapp/css/print-footer.css"],
  ["src/main/resources/static/js/resource-table.js", "src/main/webapp/js/resource-table.js"],
  ["src/main/resources/static/css/resource-table.css", "src/main/webapp/css/resource-table.css"],
  ["src/main/resources/static/js/ui-loading.js", "src/main/webapp/js/ui-loading.js"],
  ["src/main/resources/static/css/ui-loading.css", "src/main/webapp/css/ui-loading.css"],
  ["src/main/resources/static/js/select-enhancer.js", "src/main/webapp/js/select-enhancer.js"],
  ["src/main/resources/static/css/select-enhancer.css", "src/main/webapp/css/select-enhancer.css"],
  ["src/main/resources/static/js/resource-split-view.js", "src/main/webapp/js/resource-split-view.js"],
  ["src/main/resources/static/js/vendor/cloudscape-icons.LICENSE", "src/main/webapp/js/vendor/cloudscape-icons.LICENSE"],
  ["src/main/resources/static/js/vendor/cloudscape-icons.NOTICE", "src/main/webapp/js/vendor/cloudscape-icons.NOTICE"],
  ["src/main/resources/static/css/app.css", "src/main/webapp/css/app.css"],
  ["src/main/resources/static/js/app.js", "src/main/webapp/js/app.js"],
  ["src/main/resources/static/img/app-logo.png", "src/main/webapp/img/app-logo.png"],
];

for (const [source, destination] of files) {
  const outputPath = resolve(destination);
  mkdirSync(dirname(outputPath), { recursive: true });
  copyFileSync(resolve(source), outputPath);
}
