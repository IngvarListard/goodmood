/*
 * Рендер линейного графика состояния по дням (change: линейный график ленты).
 *
 * Контракт: canvas[data-gm-chart] с JSON
 *   {labels: string[], datasets: [{key, label, values: (number|null)[], colorVar}]}.
 * Данные, i18n-метки, имена CSS-переменных для цветов и aria-label генерирует
 * сервер (app.views.feed); этот файл — только отрисовка: никаких словарей,
 * текстов и хардкода цветов.
 *
 * Инициализация: DOMContentLoaded + htmx:afterSettle (покрывает hx-boost
 * навигацию и свапы фрагмента графика); data-gm-drawn защищает от повторной
 * отрисовки.
 */
(function () {
  'use strict';

  // Цвет из CSS-переменной текущей темы; хардкод цветов запрещён
  var cssVar = function (name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  };

  function drawChart(canvas) {
    var payload;
    try {
      payload = JSON.parse(canvas.dataset.gmChart);
    } catch (e) {
      return;
    }
    if (!window.Chart || !payload || !Array.isArray(payload.labels) ||
        !Array.isArray(payload.datasets)) return;

    var content = cssVar('--color-base-content') || 'CanvasText';
    var grid = 'rgba(128, 128, 128, 0.15)';

    var datasets = payload.datasets.map(function (ds) {
      var color = cssVar(ds.colorVar) || 'CanvasText';
      return {
        label: ds.label,
        data: ds.values,
        borderColor: color,
        backgroundColor: color,
        pointBackgroundColor: color,
        borderWidth: 2,
        pointRadius: 2.5,
        pointHoverRadius: 3.5,
        spanGaps: false,
        tension: 0.25
      };
    });

    new Chart(canvas, {
      type: 'line',
      data: { labels: payload.labels, datasets: datasets },
      options: {
        responsive: false,
        animation: { duration: 500 },
        plugins: {
          legend: {
            display: true,
            position: 'bottom',
            labels: {
              color: content,
              boxWidth: 10,
              boxHeight: 10,
              font: { family: 'Inter, system-ui, sans-serif', size: 10 }
            }
          },
          tooltip: { enabled: true }
        },
        scales: {
          y: {
            min: 0,
            max: 10,
            ticks: { color: content, stepSize: 5, font: { size: 10 } },
            grid: { color: grid }
          },
          x: {
            ticks: { color: content, font: { size: 9 } },
            grid: { display: false }
          }
        }
      }
    });
    canvas.setAttribute('data-gm-drawn', 'true');
  }

  function drawAll() {
    if (!window.Chart) return;
    var nodes = document.querySelectorAll('canvas[data-gm-chart]:not([data-gm-drawn])');
    for (var i = 0; i < nodes.length; i++) drawChart(nodes[i]);
  }

  document.addEventListener('DOMContentLoaded', drawAll);
  document.body.addEventListener('htmx:afterSettle', drawAll);
})();
