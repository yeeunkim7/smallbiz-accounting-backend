#!/usr/bin/env python3
"""Offline edit of the existing API-connected browser demo. No server or DB access."""
import hashlib
import json
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RAW = Path('/workspace/.local/run/portfolio-video-nov05')
OUT = ROOT / 'docs/demo'
SOURCE = RAW / 'source-for-edit.mp4'
SOURCE_MANIFEST = RAW / 'source-for-edit.json'
WORK = RAW / 'refresh'
WORK.mkdir(exist_ok=True)

def run(args):
    return subprocess.check_output(args, stderr=subprocess.PIPE, text=True)

def font(size, bold=False):
    return ImageFont.truetype('/usr/share/fonts/opentype/noto/NotoSansCJK-' + ('Bold' if bold else 'Regular') + '.ttc', size, index=1)

def stamp(t):
    m = round(t * 1000)
    return f'{m//3600000:02}:{m//60000%60:02}:{m//1000%60:02},{m%1000:03}'

# Source seconds are the original composed recording timeline, at unchanged speed.
EDITS = [
    (0, 10, '청구를 등록합니다', ['거래처와 금액을 입력합니다.', '이번 청구는 100,000원입니다.']),
    (11, 7, '받을 금액 100,000원', ['청구 상세에서', '금액과 기한을 확인합니다.']),
    (20, 10, '입금을 기록합니다', ['먼저 들어온 40,000원을', '입금 내역에 등록합니다.']),
    (31, 6, '입금은 연결 전입니다', ['입금 합계는 100,000원.', '청구에 연결해야 잔액에 반영됩니다.']),
    (40, 10, '연결할 후보를 살펴봅니다', ['후보는 아직 확정이 아닙니다.', '동일 금액·이름만으로는', '어느 청구의 입금인지 단정할 수 없어요.']),
    (51, 13, '확인은 사용자가 합니다', ['입금과 청구, 현재 잔액을 보고', '40,000원 연결을 확정합니다.']),
    (64, 6, '이제 60,000원이 남았습니다', ['확정한 40,000원이', '미수 잔액에 반영됐습니다.']),
    (72, 13, '나머지 입금도 연결합니다', ['60,000원을 추가로 확정하면', '남은 금액은 0원이 됩니다.']),
    (85, 11, '잘못 연결했다면 취소합니다', ['사유를 남기고', '60,000원 연결을 취소합니다.']),
    (97, 7, '잔액을 다시 확인합니다', ['연결을 취소해', '미수 잔액은 다시 60,000원입니다.']),
]

def backdrop(title, lines, number=None):
    im = Image.new('RGB', (1280, 960), '#F6F7F4')
    d = ImageDraw.Draw(im)
    d.text((72, 56), '차곡', font=font(29, True), fill='#006B5C')
    d.text((72, 100), 'FREE 시연', font=font(17), fill='#64726B')
    if number is None:
        d.text((72, 335), title, font=font(64, True), fill='#153E34')
        for j, line in enumerate(lines):
            d.text((76, 450+j*51), line, font=font(32), fill='#425B52')
    else:
        d.text((72, 310), f'{number:02d} / 10', font=font(18), fill='#64726B')
        d.text((72, 360), title, font=font(32, True), fill='#153E34')
        for j, line in enumerate(lines):
            d.text((72, 437+j*40), line, font=font(23), fill='#425B52')
        d.line((72, 853, 552, 853), fill='#D8E1DB', width=3)
        d.line((72, 853, 72+48*number, 853), fill='#006B5C', width=3)
        d.rounded_rectangle((705, 39, 1127, 915), radius=25, fill='#153E34')
    path = WORK / ('intro.png' if number is None else f'backdrop-{number}.png')
    im.save(path)
    return path

intro_title = '차곡'
intro_lines = ['청구와 입금을 연결하고', '남은 금액을 확인합니다.']
intro = backdrop(intro_title, intro_lines)
clips = [WORK / 'intro.mp4']
run(['ffmpeg','-y','-v','error','-loop','1','-i',str(intro),'-t','4','-r','25','-c:v','libx264','-pix_fmt','yuv420p',str(clips[0])])
chapters = [{'start':0,'duration':4,'title':intro_title,'caption':' '.join(intro_lines),'source':None}]
subs = [f'1\n{stamp(0)} --> {stamp(4)}\n차곡 · ' + ' '.join(intro_lines) + '\n']
timeline = 4
for i, (start, duration, title, lines) in enumerate(EDITS, 1):
    bg = backdrop(title, lines, i)
    clip = WORK / f'clip-{i:02d}.mp4'
    # Crop contains only the genuine recorded app viewport. All explanatory text is outside it.
    run(['ffmpeg','-y','-v','error','-ss',str(start),'-t',str(duration),'-i',str(SOURCE),'-loop','1','-i',str(bg),'-filter_complex','[0:v]crop=390:844:800:54:exact=1,setpts=PTS-STARTPTS[phone];[1:v][phone]overlay=721:55:shortest=1,format=yuv420p[out]','-map','[out]','-t',str(duration),'-r','25','-c:v','libx264','-preset','veryfast','-crf','18','-an',str(clip)])
    clips.append(clip)
    chapters.append({'start':timeline,'duration':duration,'title':title,'caption':' '.join(lines),'source':{'start':start,'endExclusive':start+duration,'speed':1}})
    subs.append(f'{i+1}\n{stamp(timeline)} --> {stamp(timeline+duration)}\n{title}\n' + ' '.join(lines) + '\n')
    timeline += duration
concat = WORK / 'concat.txt'
concat.write_text(''.join(f"file '{p}'\n" for p in clips))
final = OUT / 'receivables-demo.mp4'
run(['ffmpeg','-y','-v','error','-f','concat','-safe','0','-i',str(concat),'-c','copy','-movflags','+faststart',str(final)])
(OUT / 'captions.srt').write_text('\n'.join(subs), encoding='utf-8')
run(['ffmpeg','-y','-v','error','-ss','62','-i',str(final),'-frames:v','1',str(OUT / 'poster.png')])
info = json.loads(run(['ffprobe','-v','error','-count_frames','-show_entries','format=duration,size:stream=codec_name,width,height,pix_fmt,avg_frame_rate,nb_read_frames','-of','json',str(final)]))
assert float(info['format']['duration']) == timeline == 97
assert int(info['streams'][0]['nb_read_frames']) == 97*25
stream = info['streams'][0]
assert (stream['codec_name'], stream['width'], stream['height'], stream['pix_fmt'], stream['avg_frame_rate']) == ('h264', 1280, 960, 'yuv420p', '25/1')
content = final.read_bytes()
assert content.index(b'moov') < content.index(b'mdat')
run(['ffmpeg','-v','error','-i',str(final),'-f','null','-'])
original = json.loads(SOURCE_MANIFEST.read_text())
manifest = {
    'disclosure':'FREE 시연 · 시연용 데이터 · 실제 Java API에 연결한 Chromium 모바일 viewport 원본을 오프라인 재편집 · 음성 없음',
    'sourceCommit':original['sourceCommit'],
    'originalRecordingEvidence':{'manifest':original,'note':'기존 녹화의 검증 기록이며 이번 편집에서 API·DB·모델·통합검증을 다시 실행하지 않았습니다.'},
    'source':{'file':str(SOURCE),'sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'manifestFile':str(SOURCE_MANIFEST),'manifestSha256':hashlib.sha256(SOURCE_MANIFEST.read_bytes()).hexdigest(),'originalDurationSeconds':106,'phoneRegion':{'x':800,'y':54,'width':390,'height':844}},
    'edit':{'script':'scripts/edit-portfolio-demo.py','method':'4-second title card; source phone crop only; unchanged speed; listed intervals omit end-of-scene waits. New backdrop captions are outside app viewport.','omittedSourceIntervals':[[10,11],[18,20],[30,31],[37,40],[50,51],[70,72],[96,97],[104,106]],'hermesFootageIncluded':False,'syntheticUiOrTerminalIncluded':False},
    'checks':['ffprobeH2641280x960Yuv420p25fps','2425Frames97Seconds','completeEditedVideoDecode','faststartMoovBeforeMdat'],
    'media':info,'chapters':chapters,
}
(OUT / 'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'duration':timeline,'bytes':final.stat().st_size}))
