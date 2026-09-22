"""Genera Pedidos360-Evidencias.pptx desde resumen.json (evidencias-api-manager.ps1)
y las capturas de docs/evidencias/img/.

Uso:
    python infra/scripts/evidencias-pptx.py --json <resumen.json> --img <dir> --out <deck.pptx>
    python infra/scripts/evidencias-pptx.py --demo                        (deck de muestra)
"""
import argparse
import json
import os
import sys

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.util import Inches, Pt

IN = 914400
SW, SH = int(IN * 13.333), int(IN * 7.5)

C_DARK = RGBColor(0x1F, 0x2A, 0x44)
C_TITLE = RGBColor(0x0B, 0x3D, 0x91)
C_GREEN = RGBColor(0x1B, 0x7A, 0x43)
C_AMBER = RGBColor(0xC8, 0x56, 0x00)
C_RED = RGBColor(0xB3, 0x1B, 0x1B)
C_GRAY = RGBColor(0x60, 0x6A, 0x7A)
C_LIGHT = RGBColor(0xF4, 0xF6, 0xFA)
C_WHITE = RGBColor(0xFF, 0xFF, 0xFF)
C_MONO_BODY = RGBColor(0x24, 0x29, 0x33)


def new_prs():
    prs = Presentation()
    prs.slide_width = SW
    prs.slide_height = SH
    return prs


def blank(prs):
    return prs.slides.add_slide(prs.slide_layouts[6])


def box(slide, x, y, w, h, text, size=14, bold=False, color=C_DARK,
        align=PP_ALIGN.LEFT, font="Calibri", anchor=MSO_ANCHOR.TOP, wrap=True):
    tb = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = tb.text_frame
    tf.word_wrap = wrap
    p = tf.paragraphs[0]
    p.text = text
    p.alignment = align
    r = p.runs[0]
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.color.rgb = color
    r.font.name = font
    return tb


def bar(slide, title, sub=None):
    slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SW, In(0.95)).fill.solid()
    slide.shapes[-1].fill.fore_color.rgb = C_DARK
    slide.shapes[-1].line.fill.background()
    box(slide, 0.55, 0.12, 11.5, 0.6, title, size=26, bold=True, color=C_WHITE)
    if sub:
        box(slide, 0.55, 0.62, 11.5, 0.3, sub, size=12, color=RGBColor(0xC7, 0xD3, 0xE6))


def In(v):
    return Inches(v)


def badge(slide, x, y, w, h, text, fill=C_GREEN, size=13, color=C_WHITE, bold=True):
    sh = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, In(x), In(y), In(w), In(h))
    sh.adjustments[0] = 0.28
    sh.fill.solid()
    sh.fill.fore_color.rgb = fill
    sh.line.fill.background()
    tf = sh.text_frame
    tf.word_wrap = False
    tf.margin_left = 0
    tf.margin_right = 0
    tf.margin_top = 0
    tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.text = text
    p.alignment = PP_ALIGN.CENTER
    r = p.runs[0]
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.color.rgb = color
    r.font.name = "Consolas"
    return sh


def status_color(status):
    if status >= 500:
        return C_RED
    if status == 403:
        return C_AMBER
    if status == 401:
        return C_RED
    if 200 <= status < 300:
        return C_GREEN
    return C_GRAY


def pretty(content, maxlen=520):
    if not content:
        return "(cuerpo vacío)"
    if content.startswith("{") or content.startswith("["):
        try:
            content = json.dumps(json.loads(content), ensure_ascii=False, indent=2)
        except Exception:
            pass
    content = content.replace("\r\n", "\n")
    if len(content) > maxlen:
        content = content[:maxlen] + "\n..."
    return content


def mono_box(slide, x, y, w, h, text, size=9, color=C_MONO_BODY):
    tb = box(slide, x, y, w, h, text, size=size, font="Consolas", color=color)
    tb.text_frame.word_wrap = True
    return tb


def slide_env(prs, d):
    s = blank(prs)
    bar(s, "Pedidos360 · Arquitectura y entorno (EP2)")
    box(s, 0.55, 1.25, 12.2, 0.4,
        "Navegador (SPA + MSAL PKCE) → API Gateway (JWT authorizer) → ALB → EC2 Spring Boot",
        size=15, bold=True, color=C_TITLE)
    rows = [
        ("Tenant (IDaaS)", d.get("tenantId", ""), "Azure AD / Entra ID"),
        ("Dominio", d.get("dominio", ""), "onmicrosoft.com"),
        ("SPA clientId", d.get("spaClientId", ""), "public client · PKCE"),
        ("API clientId", d.get("apiClientId", ""), "expose: access_as_user"),
        ("Scope / Issuer", d.get("scope", ""), d.get("issuer", "")),
        ("API Manager", d.get("api", ""), "region us-east-2 · stage prod"),
        ("Web pública (S3+CloudFront)", d.get("webPublica", ""), "build production"),
        ("Backend EC2 (ALB)", d.get("alb", ""), "health check público"),
    ]
    y = 1.85
    for label, val, note in rows:
        box(s, 0.6, y, 3.7, 0.5, label, size=13, bold=True)
        box(s, 4.35, y, 6.4, 0.5, val, size=12, color=C_DARK, font="Consolas")
        box(s, 10.85, y, 2.4, 0.5, note, size=10, color=C_GRAY)
        y += 0.58
    box(s, 0.6, y + 0.05, 12.0, 0.4, "Usuarios de prueba", size=13, bold=True)
    yy = y + 0.42
    for u in d.get("usuarios", []):
        badge(s, 0.7, yy, 8.2, 0.42, f"{u.get('email', '')}", fill=C_DARK, size=12)
        badge(s, 9.1, yy, 3.6, 0.42, f"rol {u.get('rol', '')}",
              fill=C_GREEN if "ADMIN" in str(u.get('rol', '')) else C_AMBER, size=12)
        yy += 0.5


def slide_routes(prs, d, img_map):
    s = blank(prs)
    bar(s, "1 · Rutas del API Manager (API Gateway HTTP) — Indicador 1 (13%) · 2 · CORS (7%)")
    routes = [
        ("GET", "/health", "backend /health (salud)"),
        ("GET", "/api/pedidos", "listar pedidos"),
        ("GET", "/api/pedidos/{id}", "detalle por id"),
        ("POST", "/api/pedidos", "crear pedido"),
        ("PATCH", "/api/pedidos/{id}/estado", "cambiar estado"),
        ("DELETE", "/api/pedidos/{id}", "eliminar pedido"),
        ("ANY", "/{proxy+}", "catch-all con JWT (nada queda libre)"),
    ]
    x, y, cw = 0.55, 1.35, 7.6
    bh = 0.52
    box(s, x, y, cw, 0.35, "Rutas · JWT authorizer + scope access_as_user en TODAS",
        size=12, bold=True)
    y += 0.36
    for method, path, dest in routes:
        s.shapes.add_shape(MSO_SHAPE.RECTANGLE, In(x), In(y), In(cw), In(bh)).fill.solid()
        s.shapes[-1].fill.fore_color.rgb = RGBColor(0xE8, 0xEF, 0xF7)
        s.shapes[-1].line.fill.background()
        badge(s, x + 0.1, y + 0.08, 1.15, 0.36, method, fill=C_DARK, size=11)
        box(s, x + 1.4, y + 0.05, cw - 2.6, 0.4, path, size=12, font="Consolas")
        box(s, x + cw - 1.15, y + 0.05, 1.55, 0.4, dest.split("(")[0], size=9, color=C_GRAY)
        y += bh + 0.07
    cx, cy, cwid = 8.45, 1.35, 4.4
    box(s, cx, cy, cwid, 0.35, "Configuración CORS", size=12, bold=True)
    cors = [
        "AllowOrigins:  http://localhost:4200",
        "               https://d2u1dalj9nm2b1.cloudfront.net",
        "AllowMethods:  GET, POST, PATCH, DELETE, OPTIONS",
        "AllowHeaders:  Authorization, Content-Type",
        "ExposeHeaders: Authorization",
        "MaxAge:        3600",
        "Reject OPTIONS con 204 en preflight vía API Gateway",
    ]
    for i, line in enumerate(cors):
        box(s, cx + 0.1, cy + 0.38 + i * 0.3, cwid - 0.2, 0.3, line, size=10, color=C_DARK, font="Consolas")
    img = img_map.get("routes")
    if img:
        s.shapes.add_picture(img, In(0.6), In(5.35), width=In(12.1))
    else:
        box(s, 0.55, 5.28, 12.2, 1.5, "[Captura pendiente: consola AWS → API Gateway → Routes + CORS]",
            size=13, color=C_GRAY, align=PP_ALIGN.CENTER)


def slide_tenant(prs, d, img_map):
    s = blank(prs)
    bar(s, "3 · Tenant de Azure AD (IDaaS) — Indicador 3 (10%)")
    box(s, 0.6, 1.35, 12.1, 0.4, "Tenant Directory ID: " + d.get("tenantId", ""),
        size=15, bold=True, color=C_TITLE, font="Consolas")
    box(s, 0.6, 1.85, 12.1, 0.35, "Dominio: " + d.get("dominio", ""), size=13, font="Consolas")
    box(s, 0.6, 2.35, 12.1, 1.0,
        "Usuarios profesionales creados en el tenant y publicados en el access_token (claims y roles).",
        size=12, color=C_GRAY)
    y = 3.15
    for u in d.get("usuarios", []):
        badge(s, 0.7, y, 8.2, 0.44, u.get("email", ""), fill=C_DARK, size=12)
        badge(s, 9.1, y, 3.6, 0.44, "rol " + str(u.get("rol", "")),
              fill=C_GREEN if "ADMIN" in str(u.get("rol", "")) else C_AMBER, size=12)
        y += 0.52
    img = img_map.get("tenant")
    if img:
        s.shapes.add_picture(img, In(0.6), In(4.2), width=In(12.1))
    else:
        box(s, 0.55, 4.15, 12.2, 2.6, "[Captura pendiente: Azure portal → Entra ID → Overview + Users]",
            size=13, color=C_GRAY, align=PP_ALIGN.CENTER)


def slide_apps(prs, d, img_map):
    s = blank(prs)
    bar(s, "4 · Aplicaciones en el tenant — Indicador 4 (10%)")
    apps = [
        ("SPA (público)", d.get("spaClientId", ""), "redirect: localhost:4200 + CloudFront · public client flows ON"),
        ("pedidos-api", d.get("apiClientId", ""), "Expose an API → access_as_user · App roles PEDIDOS_ADMIN / _VENDEDOR"),
    ]
    y = 1.4
    for name, cid, note in apps:
        badge(s, 0.6, y, 3.0, 0.5, name, fill=C_TITLE, size=12)
        box(s, 3.75, y + 0.03, 4.6, 0.45, cid, size=11, font="Consolas")
        box(s, 8.45, y + 0.03, 4.4, 0.45, note, size=10, color=C_GRAY)
        y += 0.62
    box(s, 0.6, y + 0.1, 12.1, 0.35,
        "Claim aud = api://6da3f8f4-... · scope access_as_user · roles pedidos_admin / pedidos_vendedor (fields en minúsculas, normalizados a ROLE_PEDIDOS_*).",
        size=11, color=C_GRAY)
    img = img_map.get("apps")
    if img:
        s.shapes.add_picture(img, In(0.6), In(y + 0.6), width=In(12.1))
    else:
        box(s, 0.55, y + 0.55, 12.2, 2.2, "[Captura pendiente: App registrations + Expose an API + App roles]",
            size=13, color=C_GRAY, align=PP_ALIGN.CENTER)


def slide_login(prs, d, img_map):
    s = blank(prs)
    bar(s, "5 · Registro + Login (10%) · 6 · OIDC Authorization Code + PKCE (15%)")
    box(s, 0.6, 1.3, 12.1, 0.4, "MSAL v5 · interactionType Redirect · response_type=code", size=14, bold=True, color=C_TITLE)
    steps = [
        "1. El SPA genera code_verifier aleatorio + code_challenge (SHA-256, S256).",
        "2. /authorize?response_type=code&code_challenge=...&state=...&nonce=... → login Azure.",
        "3. Azure redirige con el code; MSAL lo canjea en el token endpoint (sin secretos).",
        "4. access_token aud=api://... scope=access_as_user → interceptor agrega Authorization: Bearer.",
        "5. El API Manager y el backend REVALIDAN firma JWKS, issuer y audience.",
    ]
    for i, st in enumerate(steps):
        box(s, 0.7, 1.85 + i * 0.42, 12.0, 0.4, st, size=12)
    box(s, 0.6, 4.05, 12.1, 0.35,
        "Nunca se usa implicit: el token nunca va en la URL; code + PKCE protege contra replay/CSRF.",
        size=11, color=C_GRAY)
    imgs = [img_map.get(k) for k in ("pkce", "login") if img_map.get(k)]
    if imgs:
        s.shapes.add_picture(imgs[0], In(0.6), In(4.6), width=In(12.1))
    else:
        box(s, 0.55, 4.55, 12.2, 2.1, "[Captura pendiente: login + DevTools /authorize?...code_challenge=S256]",
            size=13, color=C_GRAY, align=PP_ALIGN.CENTER)


def slide_matrix(prs, d, img_map):
    s = blank(prs)
    bar(s, "7/8 · JWT en todas las rutas — Matriz 401 / 403 / 200 / 201 (20% + 15%)")
    box(s, 0.55, 1.05, 12.2, 0.35, "Endpoint: " + d.get("api", ""), size=11, color=C_GRAY, font="Consolas")
    cases = d.get("casos", [])
    if not cases:
        box(s, 0.6, 2.5, 12.1, 0.5, "Sin casos: ejecutar evidencias-api-manager.ps1 con los tokens.",
            size=14, color=C_GRAY)
        return
    cw, ch, gap = 4.05, 1.42, 0.10
    x0, y0 = 0.55, 1.5
    for i, c in enumerate(cases):
        col = i % 3
        row = i // 3
        x = x0 + col * (cw + gap)
        y = y0 + row * (ch + 0.12)
        status = int(c.get("Status", 0))
        color = status_color(status)
        card = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, In(x), In(y), In(cw), In(ch))
        card.adjustments[0] = 0.06
        card.fill.solid()
        card.fill.fore_color.rgb = C_LIGHT
        card.line.color.rgb = color
        card.line.width = Pt(1.4)
        badge(s, x + 0.08, y + 0.06, 0.85, 0.34, str(status), fill=color, size=13)
        box(s, x + 1.0, y + 0.08, cw - 1.1, 0.32,
            f"{c.get('Method', '')} {c.get('Url', '')}", size=10, bold=True, font="Consolas")
        ok = str(c.get("Expected", "")) == str(status)
        box(s, x + 0.1, y + 0.44, cw - 0.2, 0.24,
            f"esperado {c.get('Expected', '-')} · {'OK' if ok else 'FALLA'} · {c.get('DurationMs', 0)} ms",
            size=9, color=C_GREEN if ok else C_RED, font="Consolas")
        body = pretty(c.get("Content", ""))
        body = body if len(body) <= 96 else body[:96] + "..."
        box(s, x + 0.1, y + 0.7, cw - 0.2, ch - 0.75, body, size=8, color=C_MONO_BODY, font="Consolas")
    img = img_map.get("headers")
    if img:
        s.shapes.add_picture(img, In(0.6), In(5.5), width=In(12.1))
    else:
        box(s, 0.55, 6.0, 12.2, 0.9,
            "[Opcional: captura DevTools Red → petición a /prod/api/pedidos → cabeceras Authorization: Bearer ...]",
            size=11, color=C_GRAY, align=PP_ALIGN.CENTER)


def slide_compliance(prs):
    s = blank(prs)
    bar(s, "Indicadores EP2 — cumplimiento 8/8 (100%)")
    rows = [
        ("1", "Rutas del API Manager", "13%"),
        ("2", "CORS en el API Manager", "7%"),
        ("3", "Crear tenant", "10%"),
        ("4", "Configurar app en tenant", "10%"),
        ("5", "Flujo registro + login", "10%"),
        ("6", "OIDC Authorization Code + PKCE", "15%"),
        ("7", "JWT en todas las rutas (401/403/200)", "20%"),
        ("8", "Evidencia de rutas + JSON", "15%"),
    ]
    y = 1.5
    total = 0
    for num, name, pct in rows:
        box(s, 0.8, y, 0.7, 0.5, num, size=16, bold=True, color=C_TITLE, align=PP_ALIGN.CENTER)
        box(s, 1.7, y, 8.0, 0.5, name, size=14)
        badge(s, 10.3, y + 0.04, 1.5, 0.42, pct, fill=C_GREEN, size=13)
        total += int(pct.replace("%", ""))
        y += 0.56
    badge(s, 10.3, y + 0.02, 1.5, 0.42, f"{total}%", fill=C_DARK, size=13)
    box(s, 1.7, y + 0.04, 8.0, 0.5, "TOTAL", size=14, bold=True)
    box(s, 0.8, y + 0.7, 11.5, 0.4,
        "Defensa: mismo JWT validado en 3 puntos (SPA→API Gateway→back-end) = defense in depth.",
        size=12, color=C_GRAY)


def mapping_images(imgdir):
    result = {}
    if not imgdir or not os.path.isdir(imgdir):
        return result
    for fn in sorted(os.listdir(imgdir)):
        low = fn.lower()
        if not low.endswith((".png", ".jpg", ".jpeg", ".webp")):
            continue
        path = os.path.join(imgdir, fn)
        mapping = {
            "tenant": "tenant",
            "user": "tenant",
            "route": "routes",
            "cors": "routes",
            "app": "apps",
            "scope": "apps",
            "expose": "apps",
            "role": "apps",
            "authorize": "pkce",
            "pkce": "pkce",
            "code_challenge": "pkce",
            "login": "login",
            "claim": "login",
            "dashboard": "login",
            "header": "headers",
            "network": "headers",
            "bearer": "headers",
            "interceptor": "headers",
        }
        for key, slot in mapping.items():
            if key in low and slot not in result:
                result[slot] = path
    return result


def demo_data():
    return {
        "generatedAt": "y-m-d h:m:s",
        "api": "https://pyb1wkfvcg.execute-api.us-east-2.amazonaws.com/prod",
        "alb": "http://pedidos360-alb-dev-2080390497.us-east-2.elb.amazonaws.com",
        "webPublica": "https://d2u1dalj9nm2b1.cloudfront.net",
        "tenantId": "0b4bca41-b3f5-427c-aeac-2dbcd055f91d",
        "dominio": "matiaspulgar.onmicrosoft.com",
        "spaClientId": "54b906c8-47fb-4066-a49a-b3aa7f05427e",
        "apiClientId": "6da3f8f4-905c-4c76-abf0-c711d0dd3926",
        "scope": "api://6da3f8f4-905c-4c76-abf0-c711d0dd3926/access_as_user",
        "issuer": "https://login.microsoftonline.com/0b4bca41-b3f5-427c-aeac-2dbcd055f91d/v2.0",
        "usuarios": [
            {"email": "maria@matiaspulgar.onmicrosoft.com", "rol": "PEDIDOS_VENDEDOR"},
            {"email": "jose@matiaspulgar.onmicrosoft.com", "rol": "PEDIDOS_ADMIN"},
        ],
        "casos": [
            {"Case": "01-sin-token-401", "Method": "GET", "Url": "/api/pedidos", "Status": 401, "Expected": "401", "DurationMs": 666, "Content": ""},
            {"Case": "02-token-invalido-401", "Method": "GET", "Url": "/api/pedidos", "Status": 401, "Expected": "401", "DurationMs": 148, "Content": ""},
            {"Case": "03-get-pedidos-200", "Method": "GET", "Url": "/api/pedidos", "Status": 200, "Expected": "200", "DurationMs": 420, "Content": '[{"id":1,"cliente":"ACME","estado":"RECIBIDO","total":25000,"creadoPor":"jose"}]'},
            {"Case": "05-post-pedidos-201", "Method": "POST", "Url": "/api/pedidos", "Status": 201, "Expected": "201", "DurationMs": 510, "Content": '{"id":12,"cliente":"Evidencia","estado":"RECIBIDO","total":25000}'},
            {"Case": "06-patch-estado-403", "Method": "PATCH", "Url": "/api/pedidos/1/estado", "Status": 403, "Expected": "403", "DurationMs": 480, "Content": '{"error":"Forbidden"}'},
            {"Case": "08-patch-estado-admin-200", "Method": "PATCH", "Url": "/api/pedidos/1/estado", "Status": 200, "Expected": "200", "DurationMs": 500, "Content": '{"id":1,"estado":"ENVIADO"}'},
            {"Case": "09-delete-pedido-admin-204", "Method": "DELETE", "Url": "/api/pedidos/1", "Status": 204, "Expected": "204", "DurationMs": 460, "Content": "(sin cuerpo)"},
            {"Case": "10-preflight-cors", "Method": "OPTIONS", "Url": "/api/pedidos (preflight CORS)", "Status": 204, "Expected": "204", "DurationMs": 90, "Content": "Access-Control-Allow-Origin: http://localhost:4200\\nAllow-Methods: GET,POST,PATCH,DELETE,OPTIONS\\nAllow-Headers: authorization,content-type"},
            {"Case": "11-backend-ec2-health", "Method": "GET", "Url": "/health (ALB directo)", "Status": 200, "Expected": "200", "DurationMs": 220, "Content": '{"status":"OK"}'},
        ],
    }


def build(prs, d, img_map):
    s = blank(prs)
    box(s, 0.8, 1.7, 11.7, 1.0, "Pedidos360 — Evidencias EP2", size=48, bold=True, color=C_TITLE, align=PP_ALIGN.CENTER)
    box(s, 0.8, 2.9, 11.7, 0.5, "DSY1107 · Desarrollo Cloud Native I · presentación individual",
        size=16, color=C_GRAY, align=PP_ALIGN.CENTER)
    box(s, 0.8, 3.9, 11.7, 0.9,
        "SPA Angular + MSAL (PKCE) → API Gateway (JWT) → ALB → EC2 Spring Boot\n"
        + "Generado: " + d.get("generatedAt", "") + " · evidencia real contra el endpoint prod",
        size=13, color=C_DARK, align=PP_ALIGN.CENTER)
    slide_env(prs, d)
    slide_routes(prs, d, img_map)
    slide_tenant(prs, d, img_map)
    slide_apps(prs, d, img_map)
    slide_login(prs, d, img_map)
    slide_matrix(prs, d, img_map)
    slide_compliance(prs)
    return prs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", default="docs/evidencias/resumen.json")
    ap.add_argument("--img", default="docs/evidencias/img")
    ap.add_argument("--out", default="docs/evidencias/Pedidos360-Evidencias.pptx")
    ap.add_argument("--demo", action="store_true")
    args = ap.parse_args()

    if args.demo:
        d = demo_data()
    else:
        with open(args.json, "r", encoding="utf-8") as fh:
            d = json.load(fh)
    img_map = mapping_images(args.img)
    prs = build(new_prs(), d, img_map)
    outdir = os.path.dirname(args.out)
    if outdir:
        os.makedirs(outdir, exist_ok=True)
    prs.save(args.out)
    print("Deck generado:", os.path.abspath(args.out))
    print("Imágenes usadas:", {k: os.path.basename(v) for k, v in img_map.items()})


if __name__ == "__main__":
    sys.exit(main())