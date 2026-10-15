// 共享测验组件：点击选项即时反馈。
// 用法：<div class="quiz"> 内每个 .q 块的 button.opt 加 data-ok="1" 标正确答案；
// .fb 元素展示反馈，可选 data-ok-text / data-no-text 自定义提示。
document.addEventListener("DOMContentLoaded", function () {
  document.querySelectorAll(".quiz .q").forEach(function (q) {
    var fb = q.querySelector(".fb");
    q.querySelectorAll("button.opt").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var ok = btn.dataset.ok === "1";
        q.querySelectorAll("button.opt").forEach(function (b) {
          b.classList.remove("correct", "wrong");
          if (b.dataset.ok === "1") b.classList.add("correct");
        });
        if (!ok) btn.classList.add("wrong");
        if (fb) {
          fb.textContent = ok
            ? fb.dataset.okText || "正确。"
            : fb.dataset.noText || "不对，再想想。";
          fb.className = "fb " + (ok ? "ok" : "no");
        }
      });
    });
  });
});
