package dev.gatzka.flowboard.ui.support;

import com.vaadin.flow.component.UI;

/** A short burst of confetti, for moments worth marking. Pure DOM and CSS, no library. */
public final class Celebration {

    private static final String CONFETTI_JS = """
            if (!window.flowboardConfetti) {
              const style = document.createElement('style');
              style.textContent = '@keyframes fb-confetti-fall { to { transform: translate3d(var(--dx), 110vh, 0) rotate(720deg); opacity: 0; } }';
              document.head.appendChild(style);
              window.flowboardConfetti = function () {
                const colors = ['#6d4ce6', '#16a085', '#f39c12', '#e84393', '#1e88e5', '#43a047'];
                const layer = document.createElement('div');
                layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:10000;overflow:hidden';
                for (let i = 0; i < 90; i++) {
                  const piece = document.createElement('div');
                  const size = 6 + Math.random() * 6;
                  piece.style.cssText = 'position:absolute;top:-12px;left:' + (Math.random() * 100) + 'vw;width:' + size + 'px;height:' + (size * 0.6)
                    + 'px;background:' + colors[i % colors.length] + ';border-radius:2px;opacity:1;--dx:' + ((Math.random() - 0.5) * 30) + 'vw;'
                    + 'animation:fb-confetti-fall ' + (1.8 + Math.random() * 1.4) + 's cubic-bezier(.2,.6,.4,1) ' + (Math.random() * 0.6) + 's forwards';
                  layer.appendChild(piece);
                }
                document.body.appendChild(layer);
                setTimeout(() => layer.remove(), 4000);
              };
            }
            window.flowboardConfetti();
            """;

    private Celebration() {
    }

    public static void confetti(UI ui) {
        ui.getPage().executeJs(CONFETTI_JS);
    }
}
