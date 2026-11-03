#!/usr/bin/env python3
"""Gera o relatório acadêmico de três páginas a partir do Markdown versionado.

Requer Python 3 e reportlab. Execute da raiz: python3 scripts/build_academic_report.py
Use --source/--output para gerar uma prévia em diretório separado.
"""
import argparse
from pathlib import Path
import re
from xml.sax.saxutils import escape
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=Path,
    default=ROOT / "docs/apresentacoes/retorno-2026-11-03.md")
parser.add_argument("--output", type=Path,
    default=ROOT / "docs/apresentacoes/retorno-2026-11-03.pdf")
args = parser.parse_args()
SOURCE = args.source.resolve()
OUTPUT = args.output.resolve()
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
NAVY = colors.HexColor("#333333")
TEAL = colors.HexColor("#333333")
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="ReportTitle", fontName="Helvetica-Bold", fontSize=22,
    leading=26, textColor=NAVY, spaceAfter=13, alignment=TA_CENTER))
styles.add(ParagraphStyle(name="ReportHeading", fontName="Helvetica-Bold", fontSize=12,
    leading=15, textColor=TEAL, alignment=TA_CENTER, spaceBefore=11, spaceAfter=6, keepWithNext=True))
styles.add(ParagraphStyle(name="ReportBody", fontName="Helvetica", fontSize=10,
    leading=14, spaceAfter=7, alignment=TA_LEFT))
styles.add(ParagraphStyle(name="ReportCell", fontName="Helvetica", fontSize=9,
    leading=12, spaceAfter=0))
styles.add(ParagraphStyle(name="ReportHeadCell", parent=styles["ReportCell"],
    fontName="Helvetica-Bold", textColor=colors.white))

def inline(text):
    text=escape(text).replace("—", "-").replace("–", "-")
    text=re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text=re.sub(r"`([^`]+)`", r"\1", text)
    text=re.sub(r"\[([^]]+)\]\(([^)]+)\)", r'<link href="\2" color="#333333">\1</link>', text)
    return text

def footer(canvas, doc):
    w,h=A4
    canvas.setStrokeColor(colors.HexColor("#D6D6D6"))
    canvas.line(43,37,w-43,37)
    canvas.setFont("Helvetica",8)
    canvas.setFillColor(colors.HexColor("#555555"))
    canvas.drawString(43,24,"VanRides  |  Retorno acadêmico  |  03/11/2026")
    canvas.drawRightString(w-43,24,str(doc.page))

story=[]
lines=SOURCE.read_text(encoding="utf-8").splitlines()
i=0
while i<len(lines):
    line=lines[i].strip()
    if not line:
        i+=1;continue
    if line=="<!-- pagebreak -->":
        story.append(PageBreak()); i+=1;continue
    if line.startswith("# "):
        story.append(Paragraph(inline(line[2:]),styles["ReportTitle"]));i+=1;continue
    if line.startswith("## "):
        story.append(Paragraph(inline(line[3:]),styles["ReportHeading"]));i+=1;continue
    if line.startswith("|"):
        rows=[]
        while i<len(lines) and lines[i].strip().startswith("|"):
            cells=[c.strip() for c in lines[i].strip().strip("|").split("|")]
            if not all(re.fullmatch(r"[:\- ]+",c) for c in cells):rows.append(cells)
            i+=1
        n=len(rows[0]); width=A4[0]-86
        ratios={2:[.38,.62],3:[.28,.37,.35]}[n]
        content=[[Paragraph(inline(c),styles["ReportHeadCell"] if r==0 else styles["ReportCell"])
                  for c in row] for r,row in enumerate(rows)]
        t=Table(content,colWidths=[width*r for r in ratios],repeatRows=1,hAlign="LEFT")
        t.setStyle(TableStyle([
            ("BACKGROUND",(0,0),(-1,0),NAVY),("VALIGN",(0,0),(-1,-1),"TOP"),
            ("LEFTPADDING",(0,0),(-1,-1),8),("RIGHTPADDING",(0,0),(-1,-1),8),
            ("TOPPADDING",(0,0),(-1,-1),7),("BOTTOMPADDING",(0,0),(-1,-1),7),
            ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.HexColor("#F5F5F5"),colors.white]),
            ("LINEBELOW",(0,0),(-1,0),.5,NAVY),
            ("LINEBELOW",(0,1),(-1,-1),.3,colors.HexColor("#DDDDDD"))]))
        story.extend([t,Spacer(1,7)]);continue
    story.append(Paragraph(inline(line),styles["ReportBody"]))
    i+=1

doc=SimpleDocTemplate(str(OUTPUT),pagesize=A4,rightMargin=43,leftMargin=43,
    topMargin=40,bottomMargin=48,title="VanRides acompanhamento do MVP",
    author="Equipe VanRides",subject="Retorno acadêmico de 3 de novembro de 2026")
doc.build(story,onFirstPage=footer,onLaterPages=footer)
print(OUTPUT)
