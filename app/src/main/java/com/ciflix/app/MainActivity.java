package com.ciflix.app;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.card.MaterialCardView;
import com.bumptech.glide.Glide;
import okhttp3.*;
import org.json.*;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class MainActivity extends AppCompatActivity {
    private static final int BG=Color.rgb(9,12,18), PANEL=Color.rgb(20,25,34), GOLD=Color.rgb(225,177,87), MUTED=Color.rgb(155,164,178), WHITE=Color.WHITE;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build();
    private String apiBase="";
    private String tmdbKey="";
    private String manifestUrl="https://raw.githubusercontent.com/mo7AmMeD64/nuvio-plugins/main/manifest.json";
    private FrameLayout frame;
    private LinearLayout page;
    private TextView status;
    private final ArrayList<JSONObject> movies=new ArrayList<>(), series=new ArrayList<>(), searchResults=new ArrayList<>();
    private final Set<String> favorites=new HashSet<>();
    private final ArrayList<PosterAdapter> adapters=new ArrayList<>();
    private ExoPlayer player;
    private PlayerView playerView;
    private int currentTab=0;
    private String lastSearch="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        apiBase=getSharedPreferences("ciflix",MODE_PRIVATE).getString("api_base","");
        tmdbKey=getSharedPreferences("ciflix",MODE_PRIVATE).getString("tmdb_key","");
        manifestUrl=getSharedPreferences("ciflix",MODE_PRIVATE).getString("plugin_manifest",manifestUrl);
        favorites.addAll(getSharedPreferences("ciflix",MODE_PRIVATE).getStringSet("favorites",new HashSet<>()));
        showHome();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable gradient(int a,int b,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});d.setCornerRadius(dp(radius));return d;}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void basePage(){
        frame=new FrameLayout(this);frame.setBackgroundColor(BG);page=column();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.addView(page);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
    }
    private void showHome(){
        currentTab=0; adapters.clear(); basePage();
        LinearLayout top=row();top.setPadding(dp(20),dp(18),dp(20),dp(10));page.addView(top);
        TextView logo=label("Cifli",27,WHITE,true),x=label("X",27,GOLD,true);top.addView(logo);top.addView(x);
        View gap=new View(this);top.addView(gap,new LinearLayout.LayoutParams(0,1,1));
        TextView profile=label("⚙",20,GOLD,true);profile.setGravity(Gravity.CENTER);top.addView(profile,new LinearLayout.LayoutParams(dp(42),dp(42)));profile.setOnClickListener(v->settings());
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("ابحث عن فيلم أو مسلسل");search.setTextSize(14);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setPadding(dp(16),0,dp(16),0);search.setBackground(bg(PANEL,13));LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(50));searchLp.setMargins(dp(18),dp(8),dp(18),dp(18));page.addView(search,searchLp);
        search.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){runSearch(search.getText().toString());return true;}return false;});
        buildHero();
        status=label(tmdbKey.isEmpty()?"أضف مفتاح TMDB من الإعدادات لعرض الأفلام":"جارٍ تحميل مكتبة الأفلام...",12,MUTED,false);status.setPadding(dp(20),dp(8),dp(20),dp(8));page.addView(status);
        addShelf("اختيارات CifliX",movies);
        addShelf("أحدث الأفلام",movies);
        addShelf("مسلسلات تستحق المتابعة",series);
        addShelf("أكمل المشاهدة",new ArrayList<JSONObject>());
        TextView footer=label("CIFLIX  •  عالم الترفيه بين يديك",10,MUTED,true);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(22),0,dp(100));page.addView(footer);
        addBottomNav();
        loadLibrary();
    }
    private void buildHero(){
        MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(22));card.setCardElevation(dp(3));card.setCardBackgroundColor(Color.rgb(30,31,37));
        LinearLayout hero=column();hero.setPadding(dp(22),dp(24),dp(22),dp(22));hero.setBackground(gradient(Color.rgb(48,35,29),Color.rgb(16,20,29),22));
        TextView eyebrow=label("C I F L I X   ORIGINAL EXPERIENCE",10,GOLD,true);hero.addView(eyebrow);
        TextView title=label("ليلتك…\nتستاهل قصة عظيمة",27,WHITE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(14);hero.addView(title,tp);
        TextView sub=label("اكتشف أفلاماً ومسلسلات تعيش تفاصيلها",12,Color.rgb(211,211,214),false);tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(8);hero.addView(sub,tp);
        TextView action=label("استكشف المكتبة   ←",12,BG,true);action.setGravity(Gravity.CENTER);action.setBackground(bg(GOLD,9));tp=new LinearLayout.LayoutParams(dp(160),dp(42));tp.topMargin=dp(18);hero.addView(action,tp);action.setOnClickListener(v->{currentTab=1;showCategory("أحدث الأفلام",movies);});
        card.addView(hero);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(dp(16),dp(4),dp(16),dp(16));page.addView(card,cp);
    }
    private void addShelf(String title,ArrayList<JSONObject> data){
        LinearLayout block=column();block.setPadding(0,dp(8),0,dp(12));
        LinearLayout heading=row();heading.setPadding(dp(20),0,dp(20),dp(10));
        TextView name=label(title,18,WHITE,true);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=label("عرض الكل  ‹",11,GOLD,true);heading.addView(more);more.setOnClickListener(v->showCategory(title,data));
        block.addView(heading);
        RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));rv.setNestedScrollingEnabled(false);rv.setClipToPadding(false);rv.setPadding(dp(13),0,dp(13),0);
        PosterAdapter adapter=new PosterAdapter(data);adapters.add(adapter);rv.setAdapter(adapter);block.addView(rv,new LinearLayout.LayoutParams(-1,dp(224)));
        page.addView(block);
    }
    private void addBottomNav(){
        LinearLayout nav=row();nav.setBackgroundColor(Color.rgb(15,19,27));nav.setElevation(dp(14));
        String[] names={"الرئيسية","أفلام","مسلسلات","قائمتي"};String[] icons={"⌂","▣","▤","♡"};
        for(int i=0;i<names.length;i++){final int tab=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);TextView ic=label(icons[i],19,i==currentTab?GOLD:MUTED,true);ic.setGravity(Gravity.CENTER);TextView tx=label(names[i],10,i==currentTab?GOLD:MUTED,i==currentTab);tx.setGravity(Gravity.CENTER);item.addView(ic);item.addView(tx);nav.addView(item,new LinearLayout.LayoutParams(0,dp(58),1));item.setOnClickListener(v->onTab(tab));}
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,dp(62),Gravity.BOTTOM);frame.addView(nav,p);
    }
    private void onTab(int tab){currentTab=tab;if(tab==0)showHome();else if(tab==1)showCategory("الأفلام",movies);else if(tab==2)showCategory("المسلسلات",series);else showFavorites();}
    private void loadLibrary(){
        if(tmdbKey.trim().isEmpty()){if(status!=null)status.setText("أضف مفتاح TMDB من الإعدادات حتى تظهر الأفلام والمسلسلات");return;}
        loadTmdb("/trending/movie/week?language=ar",movies);
        loadTmdb("/trending/tv/week?language=ar",series);
    }
    private void loadTmdb(String path,ArrayList<JSONObject> target){
        Request req=new Request.Builder().url("https://api.themoviedb.org/3"+path+"&api_key="+Uri.encode(tmdbKey)).build();
        http.newCall(req).enqueue(new Callback(){
            public void onFailure(Call c,IOException e){runOnUiThread(()->{if(status!=null)status.setText("تعذر الاتصال بـ TMDB");});}
            public void onResponse(Call c,Response r)throws IOException{try(Response res=r){if(!res.isSuccessful())throw new IOException("HTTP "+res.code());JSONArray a=new JSONObject(res.body().string()).optJSONArray("results");ArrayList<JSONObject> found=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;boolean tv=s.has("name");JSONObject o=new JSONObject();o.put("id",s.optString("id"));o.put("title",s.optString(tv?"name":"title"));o.put("type",tv?"series":"movie");o.put("poster",s.optString("poster_path").isEmpty()?"":"https://image.tmdb.org/t/p/w500"+s.optString("poster_path"));o.put("overview",s.optString("overview"));o.put("year",s.optString(tv?"first_air_date":"release_date"));o.put("rating",s.optString("vote_average"));found.add(o);}target.clear();target.addAll(found);runOnUiThread(()->{for(PosterAdapter a2:adapters)a2.notifyDataSetChanged();if(status!=null)status.setText("تم تحميل "+(movies.size()+series.size())+" عنوان");});}catch(Exception e){runOnUiThread(()->{if(status!=null)status.setText("تعذر قراءة بيانات TMDB");});}}
        });
    }
    private void request(String path,Callback callback){
        if(apiBase.isEmpty()){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("اربط مكتبة CifliX").setMessage("أدخل رابط الخادم من الإعدادات حتى تظهر عناوين مكتبتك.").setPositiveButton("الإعدادات",(d,w)->settings()).setNegativeButton("لاحقاً",null).show());return;}
        Request request=new Request.Builder().url(apiBase.replaceAll("/$","")+path).header("Accept","application/json").build();http.newCall(request).enqueue(callback);
    }
    private void loadSection(String key,ArrayList<JSONObject> target){
        request("/home?section="+key+"&page=1&limit=30",new Callback(){
            public void onFailure(Call c,IOException e){runOnUiThread(()->{if(status!=null)status.setText("تعذر الاتصال بالخادم حالياً");});}
            public void onResponse(Call c,Response r)throws IOException{try{if(!r.isSuccessful())throw new IOException("HTTP "+r.code());JSONObject data=new JSONObject(r.body().string());JSONArray arr=data.optJSONArray("items");target.clear();if(arr!=null)for(int i=0;i<arr.length();i++)target.add(arr.getJSONObject(i));runOnUiThread(()->{for(PosterAdapter a:adapters)a.notifyDataSetChanged();if(status!=null)status.setText("مكتبتك جاهزة • "+(movies.size()+series.size())+" عنوان");});}catch(Exception ex){runOnUiThread(()->{if(status!=null)status.setText("تعذر قراءة بيانات المكتبة");});}}
        });
    }
    private class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder>{
        final ArrayList<JSONObject> data;PosterAdapter(ArrayList<JSONObject> d){data=d;}
        class Holder extends RecyclerView.ViewHolder{ImageView image;TextView title,meta;Holder(View v){super(v);image=v.findViewById(101);title=v.findViewById(102);meta=v.findViewById(103);}}
        public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout card=column();card.setPadding(dp(5),dp(2),dp(5),dp(2));
            ImageView image=new ImageView(MainActivity.this);image.setId(101);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(bg(PANEL,10));card.addView(image,new LinearLayout.LayoutParams(dp(128),dp(160)));
            TextView title=label("",12,WHITE,true);title.setId(102);title.setMaxLines(1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(128),dp(25));p.topMargin=dp(5);card.addView(title,p);
            TextView meta=label("CIFLIX",10,MUTED,false);meta.setId(103);card.addView(meta);
            return new Holder(card);
        }
        public void onBindViewHolder(Holder h,int pos){JSONObject o=data.get(pos);h.title.setText(o.optString("title","بدون عنوان"));h.meta.setText(o.optString("type","movie").equals("series")?"مسلسل":"فيلم");Glide.with(MainActivity.this).load(o.optString("poster","")).centerCrop().placeholder(bg(PANEL,10)).into(h.image);h.itemView.setOnClickListener(v->showDetails(o));}
        public int getItemCount(){return data.size();}
    }
    private void showCategory(String title,ArrayList<JSONObject> data){
        adapters.clear();basePage();LinearLayout head=row();head.setPadding(dp(20),dp(22),dp(20),dp(18));TextView back=label("‹",28,GOLD,true);head.addView(back);back.setOnClickListener(v->showHome());TextView name=label(title,22,WHITE,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.setMargins(dp(12),0,0,0);head.addView(name,np);page.addView(head);
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new GridLayoutManager(this,3));list.setPadding(dp(10),0,dp(10),dp(90));list.setClipToPadding(false);list.setAdapter(new GridAdapter(data));page.addView(list,new LinearLayout.LayoutParams(-1,-1));
        addBottomNav();
    }
    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.GH>{
        final ArrayList<JSONObject> data;GridAdapter(ArrayList<JSONObject> d){data=d;}
        class GH extends RecyclerView.ViewHolder{ImageView img;TextView title;GH(View v){super(v);img=v.findViewById(201);title=v.findViewById(202);}}
        public GH onCreateViewHolder(android.view.ViewGroup p,int t){LinearLayout c=column();c.setPadding(dp(4),dp(5),dp(4),dp(8));ImageView i=new ImageView(MainActivity.this);i.setId(201);i.setScaleType(ImageView.ScaleType.CENTER_CROP);c.addView(i,new LinearLayout.LayoutParams(-1,dp(150)));TextView n=label("",11,WHITE,true);n.setId(202);n.setMaxLines(1);c.addView(n);return new GH(c);}
        public void onBindViewHolder(GH h,int pos){JSONObject o=data.get(pos);h.title.setText(o.optString("title"));Glide.with(MainActivity.this).load(o.optString("poster")).centerCrop().placeholder(bg(PANEL,8)).into(h.img);h.itemView.setOnClickListener(v->showDetails(o));}
        public int getItemCount(){return data.size();}
    }
    private void runSearch(String query){
        if(query.trim().isEmpty())return;
        if(tmdbKey.trim().isEmpty()){toast("أضف مفتاح TMDB من الإعدادات أولاً");settings();return;}
        lastSearch=query;searchResults.clear();
        Request req=new Request.Builder().url("https://api.themoviedb.org/3/search/multi?query="+Uri.encode(query.trim())+"&language=ar&api_key="+Uri.encode(tmdbKey)).build();
        http.newCall(req).enqueue(new Callback(){
            public void onFailure(Call call,IOException e){runOnUiThread(()->toast("تعذر تنفيذ البحث"));}
            public void onResponse(Call call,Response response)throws IOException{
                try(Response r=response){
                    if(!r.isSuccessful())throw new IOException("HTTP "+r.code());
                    JSONArray a=new JSONObject(r.body().string()).optJSONArray("results");
                    if(a!=null)for(int i=0;i<a.length();i++){
                        JSONObject src=a.optJSONObject(i);if(src==null)continue;
                        String media=src.optString("media_type");if(!"movie".equals(media)&&!"tv".equals(media))continue;
                        JSONObject o=new JSONObject();o.put("id",src.optString("id"));
                        o.put("title",src.optString("title",src.optString("name")));o.put("type","tv".equals(media)?"series":"movie");
                        o.put("poster",src.optString("poster_path").isEmpty()?"":"https://image.tmdb.org/t/p/w500"+src.optString("poster_path"));
                        o.put("overview",src.optString("overview"));o.put("year",src.optString("release_date",src.optString("first_air_date")));
                        o.put("rating",src.optString("vote_average"));searchResults.add(o);
                    }
                    runOnUiThread(()->showCategory("نتائج: "+query,searchResults));
                }catch(Exception e){runOnUiThread(()->toast("تعذر البحث؛ تحقق من مفتاح TMDB"));}
            }
        });
    }
    private void showFavorites(){ArrayList<JSONObject> list=new ArrayList<>();for(JSONObject o:movies)if(favorites.contains(o.optString("id")))list.add(o);for(JSONObject o:series)if(favorites.contains(o.optString("id")))list.add(o);showCategory("قائمتي",list);}
    private void showDetails(JSONObject item){
        String id=item.optString("id");if(id.isEmpty()){toast("معرّف المحتوى غير متوفر");return;}
        request("/title/"+Uri.encode(id),new Callback(){
            public void onFailure(Call c,IOException e){runOnUiThread(()->toast("تعذر تحميل التفاصيل"));}
            public void onResponse(Call c,Response r)throws IOException{try{JSONObject details=new JSONObject(r.body().string());runOnUiThread(()->detailsScreen(details));}catch(Exception e){runOnUiThread(()->toast("بيانات التفاصيل غير صالحة"));}}
        });
    }
    private void detailsScreen(JSONObject o){
        basePage();TextView back=label("‹   رجوع",14,GOLD,true);back.setPadding(dp(20),dp(18),dp(20),dp(14));page.addView(back);back.setOnClickListener(v->showHome());
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);Glide.with(this).load(o.optString("poster")).into(poster);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(170),dp(240));pp.gravity=Gravity.CENTER_HORIZONTAL;page.addView(poster,pp);
        TextView title=label(o.optString("title"),24,WHITE,true);title.setGravity(Gravity.CENTER);title.setPadding(dp(20),dp(16),dp(20),dp(5));page.addView(title);
        TextView meta=label(o.optString("year","")+"   •   ★ "+o.optString("rating","—")+"   •   "+o.optString("type"),12,GOLD,true);meta.setGravity(Gravity.CENTER);page.addView(meta);
        TextView desc=label(o.optString("overview","لا يوجد وصف متاح حالياً."),13,MUTED,false);desc.setGravity(Gravity.TOP|Gravity.RIGHT);desc.setPadding(dp(22),dp(18),dp(22),dp(18));page.addView(desc);
        LinearLayout actions=row();actions.setPadding(dp(20),dp(8),dp(20),dp(18));TextView play=actionButton("▶   تشغيل",GOLD,BG);actions.addView(play,new LinearLayout.LayoutParams(0,dp(48),1));TextView fav=actionButton(favorites.contains(o.optString("id"))?"♥":"♡   قائمتي",PANEL,WHITE);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(48),1);fp.setMargins(dp(10),0,0,0);actions.addView(fav,fp);page.addView(actions);
        play.setOnClickListener(v->loadStreams(o.optString("id"),null));fav.setOnClickListener(v->{String id=o.optString("id");if(!favorites.add(id))favorites.remove(id);getSharedPreferences("ciflix",MODE_PRIVATE).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();fav.setText(favorites.contains(id)?"♥   محفوظ":"♡   قائمتي");});
        JSONArray eps=o.optJSONArray("episodes");if(eps!=null&&eps.length()>0){TextView sh=label("الحلقات",18,WHITE,true);sh.setPadding(dp(20),dp(12),dp(20),dp(10));page.addView(sh);for(int i=0;i<eps.length();i++){JSONObject ep=eps.optJSONObject(i);if(ep==null)continue;TextView e=label("الحلقة "+ep.optInt("episode")+"     الموسم "+ep.optInt("season"),13,WHITE,false);e.setPadding(dp(20),dp(13),dp(20),dp(13));page.addView(e);e.setOnClickListener(v->loadStreams(o.optString("id"),ep.optString("id")));}}
        addBottomNav();
    }
    private TextView actionButton(String s,int color,int fg){TextView t=label(s,13,fg,true);t.setGravity(Gravity.CENTER);t.setBackground(bg(color,10));return t;}
    private void loadStreams(String id,String episode){
        String path="/title/"+Uri.encode(id)+"/streams"+(episode==null?"":"?episode_id="+Uri.encode(episode));
        request(path,new Callback(){public void onFailure(Call c,IOException e){runOnUiThread(()->toast("تعذر جلب مصادر التشغيل"));}public void onResponse(Call c,Response r)throws IOException{try{JSONObject obj=new JSONObject(r.body().string());JSONArray arr=obj.optJSONArray("streams");if(arr==null||arr.length()==0)throw new Exception("no streams");JSONObject stream=arr.getJSONObject(0);runOnUiThread(()->playVideo(stream.optString("url")));}catch(Exception e){runOnUiThread(()->toast("لا يوجد رابط تشغيل متاح"));}}});
    }
    private void playVideo(String url){
        if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))){toast("رابط الفيديو غير صالح");return;}
        basePage();TextView back=label("‹   إغلاق المشغل",14,GOLD,true);back.setPadding(dp(18),dp(16),dp(18),dp(16));page.addView(back);back.setOnClickListener(v->{releasePlayer();showHome();});
        playerView=new PlayerView(this);playerView.setUseController(true);page.addView(playerView,new LinearLayout.LayoutParams(-1,dp(240)));
        player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();
        TextView note=label("CifliX Player",13,MUTED,true);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(20),0,0);page.addView(note);
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}playerView=null;}
    private void settings(){
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);
        TextView tmdbLabel=label("مفتاح TMDB API لعرض الأفلام (مطلوب)",12,MUTED,true);box.addView(tmdbLabel);
        EditText tmdbInput=new EditText(this);tmdbInput.setSingleLine(true);tmdbInput.setHint("TMDB API Key");tmdbInput.setText(tmdbKey);tmdbInput.setTextColor(WHITE);box.addView(tmdbInput);
        TextView tmdbHelp=label("المفتاح من themoviedb.org/settings/api",10,GOLD,false);box.addView(tmdbHelp);
        TextView apiLabel=label("رابط API قديم (غير مطلوب)",12,MUTED,true);box.addView(apiLabel);
        EditText apiInput=new EditText(this);apiInput.setSingleLine(true);apiInput.setHint("https://server.example/api");apiInput.setText(apiBase);apiInput.setTextColor(WHITE);box.addView(apiInput);
        TextView repoLabel=label("رابط مستودع إضافات Nuvio",12,MUTED,true);LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.topMargin=dp(12);box.addView(repoLabel,rl);
        EditText repoInput=new EditText(this);repoInput.setSingleLine(true);repoInput.setText(manifestUrl);repoInput.setTextColor(WHITE);box.addView(repoInput);
        new AlertDialog.Builder(this).setTitle("إعدادات CifliX").setView(box).setNegativeButton("إلغاء",null).setNeutralButton("فحص المستودع",(d,w)->{String u=repoInput.getText().toString().trim();if(!u.isEmpty()){manifestUrl=u;getSharedPreferences("ciflix",MODE_PRIVATE).edit().putString("plugin_manifest",manifestUrl).apply();fetchPluginManifest();}}).setPositiveButton("حفظ",(d,w)->{apiBase=apiInput.getText().toString().trim().replaceAll("/$","");tmdbKey=tmdbInput.getText().toString().trim();manifestUrl=repoInput.getText().toString().trim();getSharedPreferences("ciflix",MODE_PRIVATE).edit().putString("api_base",apiBase).putString("tmdb_key",tmdbKey).putString("plugin_manifest",manifestUrl).apply();toast("تم حفظ الإعدادات");showHome();}).show();
    }
    private void fetchPluginManifest(){
        toast("جارٍ تحميل ملفات الإضافات...");
        new PluginRepository(this).sync(manifestUrl,(plugins,error)->{
            if(error!=null){toast(error);return;}
            StringBuilder report=new StringBuilder();
            int downloaded=0;
            for(PluginRepository.Plugin plugin:plugins){
                report.append("• ").append(plugin.name).append("  v").append(plugin.version)
                    .append("\\n").append(plugin.state).append("\\n");
                if("تم التنزيل".equals(plugin.state))downloaded++;
            }
            report.insert(0,"تم تنزيل "+downloaded+" من "+plugins.size()+" إضافة مفعّلة\\n\\n");
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("مستودع الإضافات")
                .setMessage(report.length()==0?"لا توجد إضافات مفعّلة في هذا المستودع":report.toString())
                .setPositiveButton("حسناً",null).show();
        });
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onBackPressed(){if(player!=null){releasePlayer();showHome();}else showHome();}
    @Override protected void onDestroy(){releasePlayer();super.onDestroy();}
}
