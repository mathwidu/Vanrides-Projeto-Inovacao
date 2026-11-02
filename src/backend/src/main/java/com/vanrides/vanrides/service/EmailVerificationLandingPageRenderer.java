package com.vanrides.vanrides.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public final class EmailVerificationLandingPageRenderer {

    private static final String STYLE = """
            :root {
              color-scheme: light;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              background: #eef3f7;
              color: #17212b;
            }
            * { box-sizing: border-box; }
            body {
              min-height: 100vh;
              margin: 0;
              display: grid;
              place-items: center;
              padding: 1rem;
              background: linear-gradient(145deg, #eef3f7, #f8fafc);
            }
            main { width: min(100%, 35rem); }
            .card {
              background: #ffffff;
              border: 1px solid #c8d2dc;
              border-radius: 1rem;
              box-shadow: 0 1rem 2.5rem rgba(23, 33, 43, 0.12);
              padding: clamp(1.25rem, 5vw, 2.5rem);
            }
            .eyebrow {
              color: #315b78;
              font-size: 0.875rem;
              font-weight: 700;
              letter-spacing: 0.08em;
              margin: 0 0 0.75rem;
              text-transform: uppercase;
            }
            h1, h2 { line-height: 1.2; }
            h1 { font-size: clamp(1.75rem, 7vw, 2.5rem); margin: 0; }
            h2 { font-size: 1.25rem; margin: 0 0 0.5rem; }
            p { line-height: 1.6; }
            #intro { margin: 1rem 0 1.5rem; }
            button {
              min-height: 3rem;
              min-width: 10rem;
              border: 2px solid #164e73;
              border-radius: 0.65rem;
              padding: 0.75rem 1rem;
              background: #164e73;
              color: #ffffff;
              cursor: pointer;
              font: inherit;
              font-weight: 700;
            }
            button:hover { background: #0e3d5d; }
            button:focus-visible, [tabindex="-1"]:focus-visible {
              outline: 3px solid #ffffff;
              outline-offset: 2px;
              box-shadow: 0 0 0 5px #102a43;
            }
            button:disabled { cursor: not-allowed; opacity: 0.65; }
            .result {
              margin-top: 1.5rem;
              border-left: 0.35rem solid #315b78;
              padding: 0.85rem 1rem;
              background: #edf5fa;
            }
            .result[data-kind="error"] {
              border-left-color: #9c2f2f;
              background: #fff1f1;
            }
            .result p, .recovery { margin: 0; }
            .recovery { margin-top: 1rem; color: #3a4651; }
            [hidden] { display: none !important; }
            @media (max-width: 20rem) {
              body { padding: 0.5rem; }
              .card { border-radius: 0.6rem; padding: 1rem; }
              button { width: 100%; }
            }
            @media (prefers-reduced-motion: reduce) {
              *, *::before, *::after {
                scroll-behavior: auto !important;
                transition: none !important;
              }
            }
            """;

    private static final String SCRIPT = """
            (() => {
              "use strict";
              let token = new URLSearchParams(window.location.hash.slice(1)).get("token");
              const legacyQuery = new URLSearchParams(window.location.search).has("token");
              window.history.replaceState(null, "", window.location.pathname);

              const actionPanel = document.getElementById("action-panel");
              const button = document.getElementById("confirm-button");
              const result = document.getElementById("result-panel");
              const heading = document.getElementById("result-heading");
              const message = document.getElementById("result-message");
              const recovery = document.getElementById("recovery");
              let submitting = false;

              const showResult = (title, detail, kind, focusResult) => {
                heading.textContent = title;
                message.textContent = detail;
                result.dataset.kind = kind;
                result.setAttribute("role", kind === "error" ? "alert" : "status");
                result.setAttribute("aria-live", kind === "error" ? "assertive" : "polite");
                result.hidden = false;
                if (focusResult) {
                  heading.focus();
                }
              };

              if (legacyQuery || !token) {
                button.disabled = true;
                recovery.hidden = false;
                showResult(
                  legacyQuery ? "Este link antigo não pode confirmar sua conta" : "Link de confirmação ausente",
                  "Volte ao VanRides e solicite um novo e-mail de confirmação.",
                  "error",
                  false
                );
                return;
              }

              button.addEventListener("click", async () => {
                if (submitting) {
                  return;
                }
                submitting = true;
                button.disabled = true;
                actionPanel.setAttribute("aria-busy", "true");
                showResult("Confirmando seu e-mail", "Aguarde um instante.", "progress", false);

                let terminal = false;
                const controller = new AbortController();
                const timeoutId = window.setTimeout(() => controller.abort(), 15000);
                try {
                  const response = await fetch(window.location.pathname, {
                    method: "POST",
                    headers: {
                      "Accept": "application/json",
                      "Content-Type": "application/json"
                    },
                    body: JSON.stringify({ token }),
                    credentials: "omit",
                    cache: "no-store",
                    referrerPolicy: "no-referrer",
                    redirect: "error",
                    signal: controller.signal
                  });

                  const responseType = response.headers.get("content-type") || "";
                  const responseMediaType = responseType.split(";", 1)[0].trim().toLowerCase();
                  let payload = null;
                  if (response.status === 200 && responseMediaType === "application/json") {
                    try {
                      payload = await response.json();
                    } catch (ignored) {
                      payload = null;
                    }
                  }
                  const confirmed = response.status === 200
                    && payload !== null
                    && payload.message === "E-mail verificado com sucesso."
                    && typeof payload.email === "string"
                    && payload.email.length > 0;

                  if (confirmed) {
                    terminal = true;
                    showResult(
                      "E-mail confirmado",
                      "Sua conta está pronta. Você já pode voltar ao VanRides e fazer login.",
                      "success",
                      true
                    );
                  } else if (response.status === 400) {
                    terminal = true;
                    recovery.hidden = false;
                    showResult(
                      "Não foi possível confirmar este link",
                      "O link é inválido, expirou ou já foi utilizado. Solicite um novo e-mail no VanRides.",
                      "error",
                      true
                    );
                  } else if (response.status === 429) {
                    showResult(
                      "Muitas tentativas em sequência",
                      "Aguarde alguns instantes e tente novamente.",
                      "error",
                      true
                    );
                  } else {
                    showResult(
                      "Serviço temporariamente indisponível",
                      "Tente novamente em alguns instantes.",
                      "error",
                      true
                    );
                  }
                } catch (error) {
                  const timedOut = error instanceof DOMException && error.name === "AbortError";
                  showResult(
                    timedOut ? "A confirmação demorou mais que o esperado" : "Sem conexão com o serviço",
                    timedOut
                      ? "Tente novamente. Nenhuma confirmação foi assumida."
                      : "Verifique sua internet e tente novamente.",
                    "error",
                    true
                  );
                } finally {
                  window.clearTimeout(timeoutId);
                  if (terminal) {
                    token = "";
                  }
                  submitting = false;
                  actionPanel.setAttribute("aria-busy", "false");
                  button.disabled = terminal;
                }
              });
            })();
            """;

    private static final String PAGE = """
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Confirmar e-mail | VanRides</title>
              <style>%s</style>
            </head>
            <body>
              <main aria-labelledby="page-title">
                <section class="card">
                  <p class="eyebrow">VanRides</p>
                  <h1 id="page-title">Confirme seu e-mail</h1>
                  <p id="intro">A confirmação só acontece quando você ativa o botão abaixo.</p>
                  <div id="action-panel" aria-busy="false">
                    <button id="confirm-button" type="button" aria-describedby="intro">
                      Confirmar e-mail
                    </button>
                  </div>
                  <section id="result-panel" class="result" role="status"
                           aria-live="polite" aria-atomic="true" hidden>
                    <h2 id="result-heading" tabindex="-1"></h2>
                    <p id="result-message"></p>
                  </section>
                  <p id="recovery" class="recovery" hidden>
                    Abra o app e use a opção de reenviar a confirmação para receber um link novo.
                  </p>
                </section>
              </main>
              <script>%s</script>
            </body>
            </html>
            """.formatted(STYLE, SCRIPT);

    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; "
                    + "style-src 'sha256-" + sha256(STYLE) + "'; "
                    + "script-src 'sha256-" + sha256(SCRIPT) + "'; "
                    + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    public String render() {
        return PAGE;
    }

    public String contentSecurityPolicy() {
        return CONTENT_SECURITY_POLICY;
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível", exception);
        }
    }
}
