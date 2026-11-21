"""Gera textures/font/icons.png: 5 glifos 18x18 (mesmo tamanho dos ícones de efeito) para as conquistas do Sea Sovereign.
Corpo 16x16 com sombreamento em rampa de tons + contorno colorido (selout) gerado automaticamente.
Uso: build_advancement_icons.py <saida.png> [preview.png]"""
import sys, math
from PIL import Image
out = sys.argv[1]; prev = sys.argv[2] if len(sys.argv) > 2 else None
N, S = 16, 18
def hx(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
L3 = (-0.45,-0.55,0.70)
def sph(dx,dy):   # dx,dy normalizados (-1..1) -> iluminação lambert de esfera
    r2=dx*dx+dy*dy
    if r2>=1: return -1
    nz=math.sqrt(1-r2)
    return dx*L3[0]+dy*L3[1]+nz*L3[2]
def pick(v,ramp,th):   # th decrescente; devolve ramp[i]
    for i,t in enumerate(th):
        if v>=t: return ramp[i]
    return ramp[len(th)]
FRAMES=6          # frames por ícone (igual aos ícones de efeito); ver client/IconAnim.java
FRAME=0           # frame sendo gerado agora
def mixw(col,amt,to=(255,255,255)):
    return tuple(int(col[i]+(to[i]-col[i])*amt) for i in range(3))
def shine(c,k,width=2,amt=0.6,only=lambda col:True):
    """faixa de brilho diagonal (x+y == k..k+width-1) que varre o ícone"""
    for (x,y),col in list(c.items()):
        if k<=x+y<k+width and only(col): c[(x,y)]=mixw(col[:3],amt)+(255,) if len(col)==4 else mixw(col,amt)
def sparkle(c,x,y,size,col):
    """estrelinha: 0 some, 1 ponto, 2 cruz"""
    if size>=1: put(c,x,y,col)
    if size>=2:
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)): put(c,x+dx,y+dy,mixw(hx(col) if isinstance(col,str) else col,0.0))
def put(c,x,y,col):
    if 0<=x<N and 0<=y<N: c[(x,y)]=col if isinstance(col,tuple) else hx(col)

# ============ FRUTA: usa o ícone 2D da Gomu Gomu no Mi (textures/item/gomu_fruit.png) ============
import os
ITEM=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','src','main','resources','assets','lasthorizon','textures','item','gomu_fruit.png')
def fruit():
    im=Image.open(ITEM).convert('RGBA')
    bb=im.getbbox(); im=im.crop(bb)                      # centraliza no quadro 16x16
    ox=(N-im.width)//2; oy=(N-im.height)//2
    c={}
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=im.getpixel((x,y))
            if a==0: continue
            # clareia um pouco (gama 0.78) para a fruta ler bem sobre o fundo escuro do tooltip
            f=lambda v:int(255*((v/255.0)**0.78))
            c[(x+ox,y+oy)]=(f(r),f(g),f(b),255)
    # animação: balança 1 px, faixa de brilho passa pelo corpo e uma estrelinha pisca
    bob=[0,-1,-1,0,0,0][FRAME]
    c={(x,y+bob):v for (x,y),v in c.items()}
    k=[-9,6,10,14,18,-9][FRAME]
    shine(c,k,2,0.55,only=lambda col:col[2]>col[0] and col[2]>col[1])      # só no corpo roxo
    sparkle(c,2,3,[0,0,1,2,1,0][FRAME],'#e8dcff')
    return c

# ============ RAIO (Poder) ============
def bolt():
    c={}
    poly=[(11.5,0),(2.5,8.6),(7.2,8.6),(4.0,16),(14.2,6.2),(8.8,6.2),(13.4,0)]
    def inside(px,py):
        ins=False; j=len(poly)-1
        for i in range(len(poly)):
            xi,yi=poly[i]; xj,yj=poly[j]
            if (yi>py)!=(yj>py) and px<(xj-xi)*(py-yi)/(yj-yi)+xi: ins=not ins
            j=i
        return ins
    G=['#fffde8','#fff3a0','#ffe04a','#ffc028','#f59a1c','#d06a14','#9a4210']  # rampa dourada
    fill={}
    for y in range(N):
        xs=[x for x in range(N) if inside(x+0.5,y+0.5)]
        if not xs: continue
        # separa em segmentos contíguos
        segs=[];s=xs[0];p=xs[0]
        for x in xs[1:]:
            if x!=p+1: segs.append((s,p)); s=x
            p=x
        segs.append((s,p))
        for a,b in segs:
            for x in range(a,b+1):
                w=b-a+1
                t=(y/15.0)             # gradiente vertical: amarelo -> laranja
                base=2+int(t*2.2)
                if x==a: v=0 if w>2 else 1                # aresta iluminada
                elif x==a+1 and w>3: v=1
                elif x==b: v=min(6,base+3)                # aresta sombreada
                elif x==b-1 and w>3: v=min(5,base+1)
                else: v=base
                fill[(x,y)]=v
    for (x,y),v in fill.items(): c[(x,y)]=hx(G[v])
    # brilho interno
    for (x,y) in [(9,2),(8,3),(7,4),(6,5),(5,6)]:
        if (x,y) in fill: c[(x,y)]=hx(G[0])
    # animação: pulso de brilho, faixa de luz varrendo o raio e faíscas piscando em fases diferentes
    pulse=[0.0,0.12,0.28,0.42,0.28,0.12][FRAME]
    for k2,v2 in list(c.items()): c[k2]=mixw(v2[:3],pulse*0.7,(255,250,210))
    shine(c,[2,7,12,17,22,27][FRAME],2,0.75)
    def spk(x,y,size,col):
        if all((x+dx,y+dy) not in fill for dx,dy in ((0,0),(1,0),(-1,0),(0,1),(0,-1))):
            sparkle(c,x,y,size,col)
    spk(2,2,[2,1,0,0,1,2][FRAME],'#fffbd0')
    spk(14,12,[0,0,1,2,1,0][FRAME],'#ffe680')
    return c

# ============ CAVEIRA (Preço) - base: caveira cinza-bege de referência ============
def skull():
    P={'a':'#ece4cf','b':'#d3cbb8','c':'#bbb3a2','d':'#9e9486','e':'#7e736b','k':'#3a2d24','m':'#5c4d43','n':'#80716500','t':'#ded5be','u':'#a79c8b','h':'#f6f0de'}
    mask={0:(4,11),1:(2,13),2:(1,14),3:(1,14),4:(0,15),5:(0,15),6:(0,15),7:(0,15),8:(1,14),9:(1,14),10:(1,14),11:(1,14),12:(3,12),13:(4,11),14:(4,11),15:(4,11)}
    c={}
    for y,(x0,x1) in mask.items():
        for x in range(x0,x1+1):
            col='c'
            if x<=x0+1 and y<=9: col='b'            # luz à esquerda
            if x>=x1-1: col='d'                      # sombra à direita
            if y==0: col='b' if x<8 else 'c'
            c[(x,y)]=col
    # brilho no alto do crânio (como na referência)
    for (x,y) in [(9,2),(10,2),(10,3),(11,3),(9,1)]: c[(x,y)]='a'
    c[(10,2)]='h'
    for (x,y) in [(3,2),(2,3)]: c[(x,y)]='a'
    # têmporas rebaixadas (lados)
    for y in range(5,9):
        for x in (1,2): c[(x,y)]='d'
        for x in (13,14): c[(x,y)]='d'
        c[(1,y)]='e'; c[(14,y)]='e'
    c[(2,5)]='c'; c[(13,5)]='c'
    # órbitas
    L=[(3,7),(4,7),(5,7),(6,7),(2,8),(3,8),(4,8),(5,8),(6,8),(2,9),(3,9),(4,9),(5,9),(6,9),(3,10),(4,10),(5,10)]
    R=[(15-x,y) for x,y in L]
    for (x,y) in L+R: c[(x,y)]='k'
    for (x,y) in [(5,9),(6,9),(5,10),(6,8),(4,10)]: c[(x,y)]='m'
    for (x,y) in [(15-5,9),(15-6,9),(15-5,10),(15-6,8),(15-4,10)]: c[(x,y)]='m'
    for (x,y) in [(3,6),(4,6),(5,6),(10,6),(11,6),(12,6)]: c[(x,y)]='d'     # sobrancelha sombreada
    # osso do nariz + cavidade nasal
    for (x,y) in [(7,8),(8,8),(7,9),(8,9)]: c[(x,y)]='a'
    for (x,y) in [(7,10),(8,10),(7,11),(8,11),(6,12),(9,12)]: c[(x,y)]='k'
    for (x,y) in [(7,12),(8,12)]: c[(x,y)]='m'
    # maçãs do rosto
    for (x,y) in [(2,11),(3,11),(4,11),(11,11),(12,11),(13,11)]: c[(x,y)]='a'
    for (x,y) in [(1,11),(14,11)]: c[(x,y)]='d'
    # dentes
    for x in range(4,12): c[(x,13)]='c'
    for i,x in enumerate(range(4,12)): c[(x,14)]='t' if i%2==0 else 'u'
    for x in range(4,12): c[(x,15)]='d'
    c[(7,15)]='u'; c[(8,15)]='u'
    out={}
    for k,v in c.items(): out[k]=hx(P[v][:7])
    # animação: brasa vermelha pulsando dentro das órbitas
    lvl=[0,1,2,3,2,1][FRAME]
    ember={1:('#6a2a22',None),2:('#b02a2a','#6a2a22'),3:('#ff4a3a','#b02a2a')}
    if lvl:
        core,rim=ember[lvl]
        for (x,y) in [(4,8),(4,9)]:
            for xx in (x,15-x): out[(xx,y)]=hx(core)
        if rim:
            for (x,y) in [(5,8),(5,9),(3,9)]:
                for xx in (x,15-x): out[(xx,y)]=hx(rim)
        if lvl==3:
            for xx in (4,11): out[(xx,8)]=hx('#ffc0b0')
    return out

# ============ LEME ============
def helm():
    BR=['#fbe9b4','#efc27a','#d79a4a','#b37430','#8a5222','#5e3416']
    GO=['#fffbd0','#ffe680','#ffc83a','#f09a20','#c06814','#8a4410']
    c={}; cx=cy=7.5; rot=math.radians(FRAME*45.0/FRAMES)
    for y in range(N):
        for x in range(N):
            px,py=x-cx,y-cy; d=math.hypot(px,py)
            if d<=4.7: c[(x,y)]=hx('#3a2210' if (px*L3[0]+py*L3[1])<0.2 else '#4a2c14')
            for k in range(8):
                a=k*math.pi/4+rot; ux,uy=math.cos(a),math.sin(a)
                al=px*ux+py*uy; pe=abs(-px*uy+py*ux)
                if 1.5<=al<=7.9:
                    wth=0.55 if al<6.0 else (1.15 if al<7.2 else 1.35)
                    if pe<=wth:
                        t=(px*L3[0]+py*L3[1])/max(d,1e-6)*0.8
                        col=BR[1] if t>-0.1 else BR[2]
                        if al<6.0 and pe<0.3 and t>0.2: col=BR[0]
                        if al>6.9: col=BR[0] if t>0 else BR[2]
                        c[(x,y)]=hx(col)
            if 4.4<=d<=6.2:
                dd=(d-4.4)/1.8
                t=(px*L3[0]+py*L3[1])/max(d,1e-6)*0.8
                i=2
                if t>0.5: i=0 if dd<0.5 else 1
                elif t>0.15: i=1 if dd<0.5 else 2
                elif t>-0.3: i=2 if dd<0.5 else 3
                else: i=3 if dd<0.5 else 4
                c[(x,y)]=hx(BR[i])
            if d<=2.8:
                t=sph(px/2.8,py/2.8)
                c[(x,y)]=hx(pick(t,GO,[0.9,0.65,0.35,0.05,-0.3]))
    gem=[('#ff5a4a','#ff3a3a','#c01830'),('#ff7a66','#ff4a4a','#d02838'),('#ffa090','#ff5a5a','#e04050'),('#ff7a66','#ff4a4a','#d02838'),('#ff5a4a','#ff3a3a','#c01830'),('#e04838','#e02a2a','#a01020')][FRAME]
    put(c,6,6,GO[0]); put(c,7,7,gem[0]); put(c,8,8,gem[2]); put(c,7,8,gem[1]); put(c,8,7,gem[1])
    return c

# ============ BÚSSOLA ============
def compass():
    GO=['#fffbd0','#ffe680','#ffc83a','#e8a02a','#b87420','#7a4a18']
    c={}; cx=cy=7.5
    for y in range(N):
        for x in range(N):
            px,py=x-cx,y-cy; d=math.hypot(px,py)
            if d<=7.7:
                t=(px*L3[0]+py*L3[1])/max(d,1e-6)*0.85
                if d>5.8:
                    c[(x,y)]=hx(pick(t,GO,[0.7,0.35,0.0,-0.35,-9]))
                else:
                    # face: creme com sombra interna no lado superior esquerdo
                    sh=(d>4.4 and t>0.35)
                    c[(x,y)]=hx('#d8c9a0' if sh else ('#f7f0dc' if t<0.0 or d<3.5 else '#fbf6e8'))
    # marcas dos pontos cardeais
    for (x,y) in [(7,2),(8,2),(7,13),(8,13),(2,7),(2,8),(13,7),(13,8)]: put(c,x,y,'#5a3a2a')
    for (x,y) in [(4,4),(11,4),(4,11),(11,11)]: put(c,x,y,'#c9b98e')
    put(c,7,1,'#fffbe0'); put(c,8,1,'#fffbe0')
    # agulha em losango (vermelha NE / azul SO)
    phi=math.radians(45+24*math.sin(2*math.pi*FRAME/FRAMES))   # a agulha balança em torno do nordeste
    u=(math.sin(phi),-math.cos(phi)); v=(math.cos(phi),math.sin(phi))
    for y in range(N):
        for x in range(N):
            px,py=x-cx,y-cy
            a=px*u[0]+py*u[1]; b=px*v[0]+py*v[1]
            w=2.3*(1-abs(a)/6.2)
            if abs(a)<=6.2 and abs(b)<=w:
                lit=b<-0.2   # metade voltada para a luz
                if a>0: col='#ff7a6a' if lit else '#c81c2c'
                else:   col='#7cc4ff' if lit else '#1f56c0'
                if abs(a)>4.5: col='#ff3a3a' if a>0 and lit else col
                c[(x,y)]=hx(col)
    put(c,7,7,'#ffe680'); put(c,8,8,'#b87420'); put(c,7,8,'#ffc83a'); put(c,8,7,'#ffc83a')
    # reflexo do vidro (cintila no meio do ciclo)
    for (x,y) in [(3,3),(4,2),(5,2),(3,4)]: put(c,x,y,'#ffffff')
    if FRAME in (2,3):
        for (x,y) in [(5,3),(4,4),(3,5)]: put(c,x,y,'#ffffff')
    return c

NO_OUTLINE={0}   # a fruta já tem contorno
GENS=[fruit,bolt,skull,helm,compass]

def outline(px):
    res={k:v+(255,) for k,v in px.items()}
    for y in range(-1,N+1):
        for x in range(-1,N+1):
            if (x,y) in px: continue
            nb=[(x+dx,y+dy) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)) if (x+dx,y+dy) in px]
            if not nb: continue
            r=sum(px[n][0] for n in nb)/len(nb); g=sum(px[n][1] for n in nb)/len(nb); b=sum(px[n][2] for n in nb)/len(nb)
            lowright=any(n[0]<x or n[1]<y for n in nb)    # vizinho à esquerda/acima => contorno é lado inferior/direito
            k=0.20 if lowright else 0.30
            res[(x,y)]=(int(r*k+4),int(g*k+2),int(b*k+10),255)
    return res

def build():
    global FRAME
    sheet=Image.new('RGBA',(S*len(GENS),S*FRAMES),(0,0,0,0))
    for f in range(FRAMES):
        FRAME=f
        for i,g in enumerate(GENS):
            c=g()
            o={k:(v if len(v)==4 else v+(255,)) for k,v in c.items()} if i in NO_OUTLINE else outline(c)
            for (x,y),col in o.items():
                X,Y=x+1,y+1
                if 0<=X<S and 0<=Y<S: sheet.putpixel((i*S+X,f*S+Y),col)
    # largura de avanço igual para todos: o Minecraft mede o glifo até o último pixel não transparente, então cada
    # quadro ganha 1 pixel quase invisível (alfa 1) na última coluna. Assim o texto começa no mesmo x em todas as linhas.
    for f in range(FRAMES):
        for i in range(len(GENS)):
            if sheet.getpixel((i*S+S-1,f*S))[3]==0: sheet.putpixel((i*S+S-1,f*S),(0,0,0,1))
    return sheet
if __name__=='__main__':
    sheet=build(); sheet.save(out)
    if prev:
        K=12; bgc=(30,18,44,255)
        frames=[]
        for f in range(FRAMES):
            row=sheet.crop((0,f*S,sheet.width,(f+1)*S))
            bg=Image.new('RGBA',(row.width+4,S+4),bgc); bg.alpha_composite(row,(2,2))
            frames.append(bg.resize((bg.width*K,bg.height*K),Image.NEAREST))
        # tira com todos os frames empilhados (png) e gif animado
        strip=Image.new('RGBA',(frames[0].width,sum(f.height for f in frames)),bgc)
        y=0
        for fr in frames: strip.paste(fr,(0,y)); y+=fr.height
        strip.save(prev)
        frames[0].save(prev.replace('.png','.gif'),save_all=True,append_images=frames[1:],duration=150,loop=0,disposal=2)
