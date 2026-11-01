#!/usr/bin/env python3
"""Opt-in real model verification. Never invoked by the browser check or Gradle tests."""
import argparse,json,sys,urllib.request,urllib.error
p=argparse.ArgumentParser();p.add_argument('--base',default='http://127.0.0.1:8082');p.add_argument('--date',required=True);p.add_argument('--allow-paid-call',action='store_true');a=p.parse_args()
if not a.allow_paid_call:
    print('NOT RUN: this operation can incur API usage charges; pass --allow-paid-call only after approving the cost.');sys.exit(2)
base=a.base.rstrip('/')
def req(path,body=None):
    data=None if body is None else json.dumps(body).encode()
    with urllib.request.urlopen(urllib.request.Request(base+path,data=data,headers={'Content-Type':'application/json'}),timeout=150) as r:return json.load(r)
try:
    caps=req('/api/v1/ai-review/capabilities')
    if not caps.get('configured'):
        print('NOT RUN: the server has no configured AI_API_KEY.');sys.exit(2)
    before=req('/api/v1/ai-review/summary?date='+a.date)
    result=req('/api/v1/ai-review/reviews',{'date':a.date})
    allowed={'get_daily_summary','list_business_originals','list_bank_originals','list_related_uploads'}
    trace=result['toolTrace'];assert allowed=={t['tool'] for t in trace}
    ids={e['id'] for e in result['evidence']}
    for group in ['facts','possibleCauses','checks']:
        for s in result[group]:assert s['evidenceIds'] and set(s['evidenceIds'])<=ids
    assert result['summary']==before and result['date']==a.date
    assert result['summary']['matchingStatus']=='NOT_EVALUATED'
    after=req('/api/v1/ai-review/summary?date='+a.date);assert after==before
    print(json.dumps({'actualModelCalled':True,'model':result['model'],'toolCalls':len(trace),'evidenceCount':len(ids),'citationIdsValid':True,'summaryUnchanged':True},ensure_ascii=False,indent=2))
    print('Citations are structurally valid; a human must still assess whether each interpretation is supported.')
except urllib.error.HTTPError as e:
    print('FAILED: application returned HTTP',e.code,'(provider body intentionally omitted)');sys.exit(1)
