/* 共享 quiz 组件 — 每个 lesson 引用一次即可。
   用法：给 .quiz 容器，里面有 .options li，点击后高亮；
   带 data-correct 属性的 li 是正确答案。 */
(function () {
  document.querySelectorAll('.quiz').forEach(function (quiz) {
    var opts = quiz.querySelectorAll('.options li');
    var fb = quiz.querySelector('.feedback');
    opts.forEach(function (li) {
      li.addEventListener('click', function () {
        if (quiz.dataset.answered === '1') return;
        quiz.dataset.answered = '1';
        var isCorrect = li.dataset.correct === '1';
        opts.forEach(function (o) {
          o.classList.remove('selected', 'correct', 'wrong');
          if (o.dataset.correct === '1') o.classList.add('correct');
        });
        if (!isCorrect) li.classList.add('wrong');
        if (fb) {
          fb.classList.add('show', isCorrect ? 'ok' : 'no');
        }
      });
    });
  });
})();
