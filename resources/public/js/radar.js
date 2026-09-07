/*
 * Рендер радара «роза ветров» (change add-js-radar-chart).
 *
 * Контракт: canvas[data-gm-radar] с JSON {labels: string[], values: (number|null)[]}.
 * Данные, i18n-метки и aria-label генерирует сервер (app.views.rose); этот файл —
 * только отрисовка: никаких словарей и хардкода текстов.
 *
 * Инициализация: DOMContentLoaded + htmx:afterSettle (покрывает hx-boost
 * навигацию и свапы фрагментов); data-gm-drawn защищает от повторной отрисовки.
 */
(function () {
  'use strict';

  // Цвет из CSS-переменной темы; ожидается hex — к нему дописывается альфа
  var cssVar = function (name, fallback) {
    var v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return v || fallback;
  };

  var withAlpha = function (hex, alpha) {
    return hex + alpha; // '#5b5bea' + '55'
  };

  function drawRadar(canvas) {
    var payload;
    try {
      payload = JSON.parse(canvas.dataset.gmRadar);
    } catch (e) {
      return;
    }
    if (!window.Chart || !payload || !Array.isArray(payload.labels)) return;

    var primary = cssVar('--color-primary', '#5b5bea');
    var secondary = cssVar('--color-secondary', '#7c5ce0');
    var content = cssVar('--color-base-content', '#e6e8f0');
    var grid = withAlpha(content, '1a'); // ~10%

    // V2: радиальный градиент secondary (центр) -> primary (края).
    // Scriptable-опция Chart.js: первый аргумент — {chart, ...}, не canvas-ctx.
    var gradient = function (scriptable) {
      var area = scriptable.chart.chartArea;
      if (!area) return 'transparent';
      var c = scriptable.chart.ctx;
      var cx = (area.left + area.right) / 2;
      var cy = (area.top + area.bottom) / 2;
      var r = Math.max(area.right - area.left, area.bottom - area.top) / 2;
      var g = c.createRadialGradient(cx, cy, 10, cx, cy, r);
      g.addColorStop(0, withAlpha(secondary, '55'));
      g.addColorStop(1, withAlpha(primary, '22'));
      return g;
    };

    // Glow точек — как у слайдера .gm-range
    var glowPlugin = {
      id: 'gm-glow',
      beforeDatasetsDraw: function (chart) {
        chart.ctx.save();
        chart.ctx.shadowBlur = 12;
        chart.ctx.shadowColor = primary;
      },
      afterDatasetsDraw: function (chart) {
        chart.ctx.restore();
      }
    };

    new Chart(canvas, {
      type: 'radar',
      data: {
        labels: payload.labels,
        datasets: [{
          data: payload.values,
          borderWidth: 2,
          borderJoinStyle: 'round',
          borderColor: primary,
          pointBackgroundColor: '#ffffff',
          pointBorderColor: primary,
          pointRadius: 3.5,
          pointBorderWidth: 2,
          pointHoverRadius: 3.5,
          spanGaps: true,
          backgroundColor: gradient
        }]
      },
      options: {
        responsive: false,
        animation: { duration: 600 },
        plugins: {
          legend: { display: false },
          tooltip: { enabled: false }
        },
        scales: {
          r: {
            min: 0,
            max: 10,
            ticks: { display: false, stepSize: 2.5 },
            grid: { color: grid, circular: true },
            angleLines: { color: grid },
            pointLabels: {
              color: content,
              font: { family: 'Inter, system-ui, sans-serif', size: 11, weight: '500' }
            }
          }
        }
      },
      plugins: [glowPlugin]
    });
    canvas.setAttribute('data-gm-drawn', 'true');
  }

  function drawAll() {
    if (!window.Chart) return;
    var nodes = document.querySelectorAll('canvas[data-gm-radar]:not([data-gm-drawn])');
    for (var i = 0; i < nodes.length; i++) drawRadar(nodes[i]);
  }

  document.addEventListener('DOMContentLoaded', drawAll);
  document.body.addEventListener('htmx:afterSettle', drawAll);
})();
