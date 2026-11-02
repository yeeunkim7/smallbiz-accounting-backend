#!/usr/bin/env python3
"""Render the checked-in Korean portfolio HTML to an offline A4 PDF."""
import argparse
import asyncio
import base64
import os
from pathlib import Path
import shutil
from playwright.async_api import async_playwright

ROOT = Path(__file__).resolve().parents[1]

async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'docs/portfolio/overview.pdf')
    args = parser.parse_args()
    source = ROOT / 'docs/portfolio/overview.html'
    html = source.read_text()
    image = ROOT / 'docs/screenshots/mobile-matching-home-paid.png'
    html = html.replace('../screenshots/mobile-matching-home-paid.png',
                        'data:image/png;base64,' + base64.b64encode(image.read_bytes()).decode())
    executable = os.environ.get('CHROMIUM_PATH') or shutil.which('chromium')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(**({'executable_path': executable} if executable else {}),
                                          headless=True, args=['--no-sandbox', '--disable-dev-shm-usage'])
        context = await browser.new_context()
        await context.route('**/*', lambda route: route.abort())
        page = await context.new_page()
        await page.set_content(html, wait_until='load')
        await page.pdf(path=str(args.output), format='A4', print_background=True, prefer_css_page_size=True)
        await browser.close()
    print('Portfolio PDF rendered:', args.output)

if __name__ == '__main__':
    asyncio.run(main())
