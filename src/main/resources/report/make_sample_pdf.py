#!/usr/bin/env python3
"""샘플 보고서 PDF 생성기 (Track B / 데모용).

output.example.json + context.example.json 을 읽어 PDF로 렌더한다.
라이브(Kotlin)는 동일 데이터를 openhtmltopdf 로 렌더하고, 데모 폴백용
'사전 생성 샘플 PDF'는 이 스크립트로 뽑아 Frontend/public/uploads 에 둔다.

의존성: pip install reportlab
사용:
  python make_sample_pdf.py            # 기본 경로로 출력
  python make_sample_pdf.py out.pdf    # 출력 경로 지정
"""
from __future__ import annotations
import json
import sys
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable,
)

HERE = Path(__file__).resolve().parent
OUT_DEFAULT = (HERE.parent.parent.parent.parent.parent
               / "Frontend" / "public" / "uploads" / "sample-report.pdf")

def _register_fonts():
    """로컬에 한글 TTF가 있으면 임베드(어디서나 동일 렌더), 없으면 Adobe-Korea1 CID 폴백.
    임베드 폰트 강제 지정: 환경변수 REPORT_FONT=/path/to/Korean.ttf"""
    import os, glob
    from reportlab.pdfbase.ttfonts import TTFont
    cands = [os.environ["REPORT_FONT"]] if os.environ.get("REPORT_FONT") else []
    for pat in ("/usr/share/fonts/**/Nanum*Gothic*.ttf",
                "/usr/share/fonts/**/NotoSans*KR*.ttf",
                "/usr/share/fonts/**/Pretendard*.ttf",
                "/Library/Fonts/*Gothic*.ttf", "C:/Windows/Fonts/malgun.ttf",
                "**/Pretendard*.ttf", "**/Nanum*Gothic*.ttf"):
        cands += glob.glob(pat, recursive=True)
    for path in cands:
        try:
            pdfmetrics.registerFont(TTFont("KoSans", path))
            return "KoSans", "KoSans"
        except Exception:
            continue
    pdfmetrics.registerFont(UnicodeCIDFont("HYGothic-Medium"))
    pdfmetrics.registerFont(UnicodeCIDFont("HYSMyeongJo-Medium"))
    return "HYGothic-Medium", "HYSMyeongJo-Medium"


SANS, SERIF = _register_fonts()

ORANGE = colors.HexColor("#E85D1F")
INK900 = colors.HexColor("#1B2330")
INK600 = colors.HexColor("#54607A")
LINE = colors.HexColor("#E5E8EF")
SOFT = colors.HexColor("#FBF4F0")
SEP = " | "


def won(n):
    return f"{n:,}원"


def man(n):
    return f"{round(n / 10000):,}만원"


def style(sz, c=INK900, f=SANS, lead=None, sp=2):
    return ParagraphStyle("s", fontName=f, fontSize=sz, textColor=c,
                          leading=lead or sz * 1.45, spaceAfter=sp,
                          alignment=TA_LEFT, wordWrap="CJK")


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT_DEFAULT
    out.parent.mkdir(parents=True, exist_ok=True)
    ctx = json.loads((HERE / "context.example.json").read_text(encoding="utf-8"))
    o = json.loads((HERE / "output.example.json").read_text(encoding="utf-8"))
    body = style(9.3, INK600, lead=15)

    doc = SimpleDocTemplate(str(out), pagesize=A4,
                            leftMargin=18 * mm, rightMargin=18 * mm,
                            topMargin=15 * mm, bottomMargin=14 * mm,
                            title="상권 분석 보고서 (샘플)")
    E = []
    E.append(Paragraph("상권을 부탁해  |  AI 상권 분석 보고서", style(8.5, ORANGE, sp=1)))
    E.append(Paragraph(o["headline"], style(16, INK900, lead=21, sp=2)))
    meta = SEP.join([ctx["location"]["roadAddress"], ctx["meta"]["businessType"]["label"],
                     ctx["meta"]["region"], ctx["meta"]["generatedAt"][:10]])
    E.append(Paragraph(meta, style(8.5, INK600, sp=4)))
    E.append(HRFlowable(width="100%", thickness=1, color=LINE, spaceAfter=10))

    score = f'{ctx["market"]["score"]}점  |  {o.get("grade", "")}'
    kpis = [["입지 점수", "추정 월매출", "지역 평균", "예상 회수기간"],
            [score, man(o["estimatedRevenue"]["monthlyKrw"]),
             man(o["estimatedRevenue"]["areaAvgKrw"]), f'{o["roi"]["months"]}개월']]
    t = Table(kpis, colWidths=[42 * mm] * 4)
    t.setStyle(TableStyle([
        ("FONT", (0, 0), (-1, 0), SANS, 8), ("FONT", (0, 1), (-1, 1), SANS, 13),
        ("TEXTCOLOR", (0, 0), (-1, 0), INK600), ("TEXTCOLOR", (0, 1), (-1, 1), ORANGE),
        ("BACKGROUND", (0, 0), (-1, -1), SOFT), ("BOX", (0, 0), (-1, -1), 0.8, LINE),
        ("INNERGRID", (0, 0), (-1, -1), 0.8, colors.white),
        ("TOPPADDING", (0, 0), (-1, -1), 7), ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("LEFTPADDING", (0, 0), (-1, -1), 10)]))
    E.append(t)
    E.append(Spacer(1, 12))

    def head(num, txt):
        E.append(Paragraph(f'<font color="#E85D1F">{num}</font> {txt}', style(11, INK900, sp=5)))

    head("01", "전문가 총평")
    for para in o["summary"].split("\n\n"):
        E.append(Paragraph(para.strip(), body))
    E.append(Spacer(1, 9))

    head("02", "수익성 — 투자 회수와 손익분기")
    roi, bep = o["roi"], o["bep"]
    E.append(Paragraph(f'초기 투자금 <b>{man(roi["initialInvestmentKrw"])}</b>, 추정 월 순이익 '
                       f'<b>{man(roi["monthlyNetProfitKrw"])}</b> 기준 약 <b>{roi["months"]}개월</b>이면 '
                       f'회수가 가능합니다.', body))
    E.append(Paragraph(f'손익분기를 넘기려면 {bep["peakWindow"]} 피크에 하루 '
                       f'<b>{bep["minPeakUnitsPerDay"]}잔</b> 이상, 추정매출 달성은 '
                       f'<b>{bep["targetPeakUnitsPerDay"]}잔</b> 수준이 필요합니다. '
                       f'(객단가 {won(bep["avgTicketPriceKrw"])} · {bep["basis"]})', body))
    E.append(Paragraph(" / ".join(roi["assumptions"]), style(8, INK600, lead=13, sp=2)))
    E.append(Spacer(1, 9))

    head("03", "추천 창업 방향")
    rc = o["recommendation"]
    rows = [("컨셉", rc["concept"]), ("메뉴", " / ".join(rc["menu"])),
            ("가격대", rc["priceBand"]), ("인테리어", rc["interior"]), ("타깃", rc["targetCustomer"])]
    rt = Table([[Paragraph(f'<b>{k}</b>', style(9, ORANGE)), Paragraph(v, body)] for k, v in rows],
               colWidths=[20 * mm, 152 * mm])
    rt.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"),
                            ("TOPPADDING", (0, 0), (-1, -1), 3), ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
                            ("LINEBELOW", (0, 0), (-1, -2), 0.5, LINE)]))
    E.append(rt)
    E.append(Spacer(1, 9))

    head("04", "주변 경쟁점 분석 (리뷰 기반)")
    cp = o["competition"]
    E.append(Paragraph(cp["summary"], body))
    E.append(Paragraph(f'<font color="#1D9E75"><b>기회 시그널</b></font> {", ".join(cp["positiveSignals"])}',
                       style(8.6, INK600, sp=2)))
    E.append(Paragraph(f'<font color="#D85A30"><b>주의 시그널</b></font> {", ".join(cp["cautions"])}',
                       style(8.6, INK600, sp=6)))

    head("05", "리스크 · 기회")

    def bullets(title, arr, col):
        E.append(Paragraph(f'<font color="{col}"><b>{title}</b></font>', style(9, INK900, sp=3)))
        for it in arr:
            E.append(Paragraph(f'- <b>{it["title"]}</b> : {it["detail"]}', style(8.6, INK600, lead=13, sp=3)))

    bullets("리스크", o["risks"], "#D85A30")
    bullets("기회", o["opportunities"], "#1D9E75")
    E.append(Spacer(1, 8))

    E.append(HRFlowable(width="100%", thickness=0.8, color=LINE, spaceAfter=6))
    for d in o["disclaimers"]:
        E.append(Paragraph("* " + d, style(7.2, INK600, f=SERIF, lead=10, sp=1)))

    doc.build(E)
    print(f"[OK] {out}  ({out.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
