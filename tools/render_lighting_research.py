#!/usr/bin/env python3
"""Build the offline lighting research review with original, hash-verified captures.
Prior reference images are extracted byte-for-byte from the published 0.6.0 report.
New diagnostic captures default to the local scratch directory; pass --captures to override.
"""
from pathlib import Path
import argparse,base64,hashlib,html,json,re

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--captures',type=Path,default=Path('/workspace/scratch/aurum-lighting-unification/captures'))
args=parser.parse_args()

def inline(s):
    s=html.escape(s)
    s=re.sub(r'`([^`]+)`',r'<code>\1</code>',s)
    s=re.sub(r'\[([^\]]+)\]\((https://[^)]+)\)',r'<a href="\2">\1</a>',s)
    return s

def markdown(s):
    out=[];paragraph=[];table=[];items=[]
    def flush():
        if paragraph:out.append('<p>'+inline(' '.join(paragraph))+'</p>');paragraph.clear()
        if table:
            head=table[0];rows=table[2:]
            out.append('<div class="table-scroll"><table><thead><tr>'+''.join('<th>'+inline(c)+'</th>' for c in head)+'</tr></thead><tbody>')
            out.extend('<tr>'+''.join('<td>'+inline(c)+'</td>' for c in row)+'</tr>' for row in rows)
            out.append('</tbody></table></div>');table.clear()
        if items:out.append('<ul>'+''.join('<li>'+inline(c)+'</li>' for c in items)+'</ul>');items.clear()
    for line in s.splitlines():
        if not line.strip():flush()
        elif line.startswith('#'):
            flush();n=len(line)-len(line.lstrip('#'));out.append(f'<h{n}>'+inline(line[n:].strip())+f'</h{n}>')
        elif line.startswith('|'):
            if paragraph or items:flush()
            table.append([x.strip() for x in line.strip('|').split('|')])
        elif re.match(r'^(?:- |\d+\. )',line):
            if paragraph or table:flush()
            items.append(re.sub(r'^(?:- |\d+\. )','',line))
        elif items:items[-1]+=' '+line.strip()
        else:paragraph.append(line.strip())
    flush();return '\n'.join(out)

manifest=[]
def figure(data,title,caption,record):
    digest=hashlib.sha256(data).hexdigest()
    if record.get('sha256'):assert digest==record['sha256'],title
    manifest.append(dict(record,sha256=digest,title=title,caption=caption))
    return '<figure><button class="image" aria-label="Enlarge '+html.escape(title,quote=True)+'"><img loading="lazy" src="data:image/png;base64,'+base64.b64encode(data).decode()+'" alt="'+html.escape(title,quote=True)+'"></button><figcaption><strong>'+html.escape(title)+'</strong><p>'+html.escape(caption)+'</p></figcaption></figure>'

prior=json.loads((ROOT/'docs/reports/aurum-0.6.0-capture-manifest.json').read_text())
needed={prior['captures'][k]['sha256']:k for k in ['rvNoon','voxelOff','voxelOn']}
old_images={}
for b64 in re.findall(r'data:image/png;base64,([A-Za-z0-9+/=]+)',(ROOT/'docs/reports/aurum-0.6.0-visual-review.html').read_text()):
    data=base64.b64decode(b64);digest=hashlib.sha256(data).hexdigest()
    if digest in needed:old_images[needed[digest]]=data
assert len(old_images)==3
reference=figure(old_images['rvNoon'],'Rethinking Voxels · historical daylight reference',
    'Original game capture from the 0.6.0 research session, HIGH. Not a new cave comparison; earlier reference run had distant foliage gaps. This image cannot validate our lighting rewrite.',
    dict(prior['captures']['rvNoon'],evidenceType='historical reference',sourceManifest='aurum-0.6.0-capture-manifest.json'))
legacy=''.join(figure(old_images[k],title,caption,dict(prior['captures'][k],evidenceType='historical Aurum fixture',sourceManifest='aurum-0.6.0-capture-manifest.json')) for k,title,caption in [
 ('voxelOff','Earlier fixture · propagated block light','Aurum 0.6.0. Historical fixture control; useful for source visibility, insufficient for block-entity, dynamic-light or cavern integration acceptance.'),
 ('voxelOn','Earlier fixture · local voxel light','Aurum 0.6.0, detail 8. Direct shadows and colour in one fixture did not establish one coherent lighting system. This is not the new implementation.')])
new=[]
labels={
 'audit-normal':('Current room · normal rendering','0.6.1 development candidate. Constructed diagnostic room in a disposable copy of the scenery save. This is a tooling check, not the user’s cave or a visual fix.'),
 'audit-paths':('Which objects receive Aurum lighting?','Cyan marks Sodium terrain; heads/chests keep their core-shader appearance. Water would be purple. Core objects are deliberately not presented as receiving these diagnostic terms.'),
 'audit-coverage':('Where voxel replacement actually applies','Green means effective voxel replacement; red means propagated fallback. Intermediate colours show blending. This map displays applied coverage, not source completeness or correctness.'),
 'audit-block':('Composed local block illumination','Actual terrain block-light term, including its local AO factor, before albedo. Post effects bypassed; core objects retain normal shading.'),
 'audit-fallback':('Propagated block-light control','The same decoded lightmap curve and AO without voxel replacement. This isolates the current fallback term; it does not recover independent dynamic sources.'),
 'audit-voxel':('Eligible voxel illumination','The raw eligible atlas sample scaled to terrain’s light units, including local AO, before albedo. Boundary confidence is separate; this is not a GI view.'),
 'audit-sky':('Sky and current ambient fill','Actual terrain ambient term with local AO, before albedo. Includes existing screen-average bounce tint if enabled; the fixture disables it. Core objects remain unchanged.'),
 'audit-direct':('Direct sun or moon term','A sealed room is a negative control for terrain sunlight. Core objects still use their own shader path. This is not a positive sunshaft test.'),
 'audit-normal-map':('Geometry normal isolation','Derivative geometry normal mapped to RGB. This separates geometric orientation from lighting. It is not the generated normal or height-map view.'),
 'audit-ao':('Terrain AO isolation','The terrain AO value after the existing adjustments. The separate post AO pass is bypassed; this is not a combined-occlusion view.'),
 'audit-restored':('Normal rendering restored','Diagnostic OFF restores the normal path without changing lighting settings. Animated furnace pixels and time-dependent effects can differ; this is not a pixel-identical release regression proof.')}
for stem,(title,caption) in labels.items():
    p=args.captures/(stem+'.png')
    if p.exists():
        metadata=json.loads(p.with_suffix('.json').read_text())
        new.append(figure(p.read_bytes(),title,caption,dict(metadata,evidenceType='current diagnostic smoke test')))

css='''*{box-sizing:border-box}html{color-scheme:dark;background:#0c1518;color:#e5ece9;font:17px/1.65 system-ui,sans-serif}body{margin:0;overflow-wrap:anywhere}main,header{max-width:1280px;margin:auto;padding:36px 28px}header{padding-top:70px}h1{font:clamp(36px,5vw,70px)/1.1 Georgia,serif;max-width:1000px}h2{font:32px/1.25 Georgia,serif;margin-top:55px}h3{font-size:23px;color:#f3be7e;margin-top:30px}p,li{max-width:100ch}a{color:#9de5e2}code{font-size:.86em;background:#1b2b31;padding:2px 5px;overflow-wrap:anywhere}.eyebrow{font:700 12px/1.5 system-ui;letter-spacing:.16em;color:#f3be7e;text-transform:uppercase}.lead{font-size:21px;max-width:850px}.notice{border-left:4px solid #eeb87c;background:#18272c;padding:20px 24px}.cards,.gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}.cards>div{background:#14242a;border:1px solid #355059;padding:20px}.cards h3{margin-top:0}figure{margin:0;background:#14242a;border:1px solid #355059;min-width:0}figure img{width:100%;display:block;image-rendering:auto}.image{display:block;width:100%;border:0;padding:0;background:transparent;cursor:zoom-in}figcaption{padding:18px}figcaption p{font-size:14px;color:#bbcacc;margin:8px 0}.table-scroll{overflow-x:auto;margin:24px 0}table{border-collapse:collapse;width:100%;font-size:14px;min-width:640px}th,td{padding:14px;text-align:left;vertical-align:top;border-bottom:1px solid #355059}th{background:#1c3037;color:#f3be7e}section{margin:30px 0}details{margin-top:30px;padding:20px;background:#14242a}summary{cursor:pointer;color:#9de5e2}dialog{padding:12px;border:1px solid #6a929b;max-width:96vw;max-height:96vh;background:#0c1518}dialog::backdrop{background:#000c}dialog img{max-width:90vw;max-height:80vh}dialog button{display:block;padding:8px 20px;margin:0 0 10px auto;cursor:pointer}@media(max-width:700px){main,header{padding:24px 16px}.cards,.gallery{grid-template-columns:1fr}h2{font-size:28px}.lead{font-size:18px}}'''
body='''<header><p class="eyebrow">Aurum · lighting research · 24 October 2026</p><h1>Lighting, as one system.</h1><p class="lead">Rethinking Voxels is the primary reference. Our first task is shared illumination across terrain, objects and air—with caves as the acceptance scene.</p><p class="notice"><strong>Status: research + development diagnostics.</strong> The reported clash remains unfixed. No new visual release is being presented here. The downloadable 0.6.1 JAR is unchanged.</p></header><main><section class="cards"><div><h3>The architectural gap</h3><p>Terrain, core-rendered objects, local voxel receivers and fog do not share one lighting contract.</p></div><div><h3>The engineering direction</h3><p>One source authority, separate direct and indirect terms, explicit coverage, compatible material responses, and source-aware air.</p></div></section><section><h2>The reference</h2>'''+reference+'''</section><section><h2>Why the earlier fixture was insufficient</h2><p>These are earlier real captures. They tested an isolated feature; they did not test the integration failures reported in gameplay.</p><div class="gallery">'''+legacy+'''</div></section><section><h2>New diagnostic engineering</h2><p>Commands operate only in the development client. Contribution views isolate terrain before material colour and bypass post composition. They explicitly do not unify unsupported render paths.</p><div class="gallery">'''+''.join(new)+'''</div></section><section>'''+markdown((ROOT/'docs/research/LIGHTING_UNIFICATION.md').read_text())+'''</section><details><summary>Capture provenance and exact snapshot data</summary><p>Original PNGs are embedded byte-for-byte. The adjacent JSON evidence file contains hashes, configuration and snapshot data for each frame. Historical and current captures are labelled separately.</p><p><a href="https://raw.githubusercontent.com/atobouh/aurum/codex/docs/research/lighting-unification-evidence.json">Download evidence JSON</a></p></details></main><dialog><button>Close</button><img alt="Enlarged original capture"></dialog><script>const d=document.querySelector('dialog');document.querySelectorAll('.image').forEach(b=>b.onclick=()=>{d.querySelector('img').src=b.querySelector('img').src;d.showModal()});d.querySelector('button').onclick=()=>d.close();</script>'''
output=ROOT/'docs/reports/aurum-lighting-unification-research.html'
output.write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aurum — lighting unification research</title><style>'+css+'</style>'+body+'</html>')
evidence={'baseline':'Aurum 0.6.1 / d10830d; diagnostics candidate, no visual fix','referenceCommit':'d76c831da62b695d1b30d30bb218aba2116fd55e','captures':manifest,'newDiagnosticCount':len(new),'limits':['No newly matched Rethinking Voxels cave capture','No Intel UHD measurement','Not the full user modpack or actual save','Diagnostic success does not mean reported lighting clash fixed']}
(ROOT/'docs/research/lighting-unification-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(f'{output}: {len(manifest)} original images, {output.stat().st_size} bytes; {len(new)} current diagnostic frames')
