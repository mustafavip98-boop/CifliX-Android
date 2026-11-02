package com.ciflix.app;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.content.*;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
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
    private final int BG=Color.rgb(13,16,22), PANEL=Color.rgb(24,29,38), GOLD=Color.rgb(232,184,92), MUTED=Color.rgb(151,160,174), WHITE=Color.WHITE;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(18,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build();
    private String apiBase=""; private LinearLayout root,content; private TextView status; private final ArrayList<JSONObject> movies=new ArrayList<>(), series=new ArrayList<>(); private final ArrayList<PosterAdapter> adapters=new ArrayList<>(); private ExoPlayer player;
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);apiBase=getSharedPreferences("ciflix",MODE_PRIVATE).getString("api_base","");buildUI();loadAll();}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void buildUI(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);scroll.addView(root);setContentView(scroll);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(20),dp(18),dp(20),dp(12));root.addView(top);
        TextView logo=text("Cifli",25,WHITE,true);TextView x=text("X",25,GOLD,true);top.addView(logo);top.addView(x);View spacer=new View(this);top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        TextView settings=text("⚙",23,WHITE,true);settings.setGravity(Gravity.CENTER);top.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));settings.setOnClickListener(v->settings());
        TextInputEditText search=new TextInputEditText(this);search.setSingleLine(true);search.setTextColor(WHITE);search.setHintTextColor(MUTED);search.setHint("ابحث عن فيلم أو مسلسل");search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setPadding(dp(16),0,dp(16),0);search.setBackground(shape(PANEL,dp(14)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.setMargins(dp(18),0,dp(18),dp(18));root.addView(search,sp);search.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){search(search.getText().toString());return true;}return false;});
        MaterialCardView hero=new MaterialCardView(this);hero.setRadius(dp(22));hero.setCardBackgroundColor(Color.rgb(34,37,44));hero.setStrokeColor(Color.rgb(74,63,45));hero.setStrokeWidth(dp(1));LinearLayout h=new LinearLayout(this);h.setOrientation(1);h.setPadding(dp(22),dp(22),dp(22),dp(22));hero.addView(h);TextView tag=text("CIFLIX  •  YOUR CINEMA",11,GOLD,true);h.addView(tag);TextView headline=text("ليلتك تستاهل\\nفيلم.",29,WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(10);h.addView(headline,hp);TextView sub=text("اكتشف مكتبة أفلامك ومسلسلاتك المفضلة",13,MUTED,false);hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(8);h.addView(sub,hp);LinearLayout.LayoutParams he=new LinearLayout.LayoutParams(-1,-2);he.setMargins(dp(16),0,dp(16),dp(24));root.addView(hero,he);
        status=text("نتصل بالمكتبة...",13,MUTED,false);status.setPadding(dp(20),dp(6),dp(20),dp(6));root.addView(status);
        addSection("أحدث الأفلام",movies,"latest-movies");addSection("مسلسلات تستحق المتابعة",series,"latest-series");
        TextView footer=text("CIFLIX  •  مشاهدة ممتعة",11,MUTED,false);footer.setGravity(Gravity.CENTER);footer.setPadding(0,dp(30),0,dp(90));root.addView(footer);
        LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.rgb(18,22,29));nav.setGravity(Gravity.CENTER);String[] labels={"⌂  الرئيسية","▣  أفلام","▤  مسلسلات","♡  قائمتي"};for(String l:labels){TextView n=text(l,12,WHITE,true);n.setGravity(Gravity.CENTER);nav.addView(n,new LinearLayout.LayoutParams(0,dp(58),1));}FrameLayout frame=new FrameLayout(this);frame.addView(scroll);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,dp(62),Gravity.BOTTOM);frame.addView(nav,np);setContentView(frame);
    }
    private void addSection(String title,ArrayList<JSONObject> data,String key){LinearLayout block=new LinearLayout(this);block.setOrientation(1);block.setPadding(0,dp(8),0,dp(18));TextView heading=text(title,19,WHITE,true);heading.setPadding(dp(20),dp(8),dp(20),dp(14));block.addView(heading);RecyclerView rv=new RecyclerView(this);rv.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));PosterAdapter adapter=new PosterAdapter(data);adapters.add(adapter);rv.setAdapter(adapter);rv.setPadding(dp(14),0,dp(14),0);rv.setClipToPadding(false);block.addView(rv,new LinearLayout.LayoutParams(-1,dp(224)));root.addView(block);}
    private void request(String path, okhttp3.Callback cb){if(apiBase.isEmpty()){runOnUiThread(()->status.setText("اربط عنوان API من ⚙ حتى تظهر المكتبة."));return;}Request req=new Request.Builder().url(apiBase.replaceAll("/$","")+path).header("Accept","application/json").build();http.newCall(req).enqueue(cb);}
    private void loadAll(){load("latest-movies",movies);load("latest-series",series);}
    private void load(String section,ArrayList<JSONObject> target){request("/home?section="+section+"&page=1&limit=30",new Callback(){public void onFailure(Call c,IOException e){runOnUiThread(()->status.setText("تعذر الاتصال بالخادم. تحقق من رابط API والإنترنت."));}public void onResponse(Call c,Response r)throws IOException{try{JSONObject o=new JSONObject(r.body().string());JSONArray a=o.optJSONArray("items");target.clear();if(a!=null)for(int i=0;i<a.length();i++)target.add(a.getJSONObject(i));runOnUiThread(()->{for(PosterAdapter adapter:adapters)adapter.notifyDataSetChanged();status.setText("مكتبتك جاهزة • "+(movies.size()+series.size())+" عنوان");});}catch(Exception e){runOnUiThread(()->status.setText("وصل رد غير مفهوم من الخادم."));}}});}
    private void search(String q){if(q.trim().isEmpty())return;request("/search?q="+Uri.encode(q),new Callback(){public void onFailure(Call c,IOException e){runOnUiThread(()->status.setText("فشل البحث."));}public void onResponse(Call c,Response r)throws IOException{try{JSONArray a=new JSONObject(r.body().string()).optJSONArray("items");ArrayList<JSONObject> found=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)found.add(a.getJSONObject(i));runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("نتائج البحث").setMessage(found.size()+" نتيجة متاحة.").setPositiveButton("حسناً",null).show());}catch(Exception ignored){}}});}
    private void settings(){EditText input=new EditText(this);input.setSingleLine(true);input.setHint("https://your-api-domain.com/api");input.setText(apiBase);new AlertDialog.Builder(this).setTitle("إعداد اتصال CifliX").setMessage("أدخل رابط API العام").setView(input).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{apiBase=input.getText().toString().trim().replaceAll("/$","");getSharedPreferences("ciflix",MODE_PRIVATE).edit().putString("api_base",apiBase).apply();loadAll();}).show();}
    private class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.H>{ArrayList<JSONObject> list;PosterAdapter(ArrayList<JSONObject> l){list=l;}class H extends RecyclerView.ViewHolder{ImageView img;TextView title,meta;H(View v){super(v);img=v.findViewById(1);title=v.findViewById(2);meta=v.findViewById(3);}}
        public H onCreateViewHolder(android.view.ViewGroup p,int t){LinearLayout card=new LinearLayout(MainActivity.this);card.setOrientation(1);card.setPadding(dp(5),dp(3),dp(5),dp(3));ImageView image=new ImageView(MainActivity.this);image.setId(1);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new LinearLayout.LayoutParams(dp(126),dp(158)));TextView name=text("",13,WHITE,true);name.setId(2);name.setMaxLines(1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(126),dp(28));lp.topMargin=dp(5);card.addView(name,lp);TextView meta=text("CIFLIX",10,MUTED,false);meta.setId(3);card.addView(meta);return new H(card);}
        public void onBindViewHolder(H h,int pos){JSONObject o=list.get(pos);String name=o.optString("title","بدون عنوان"),poster=o.optString("poster","");h.title.setText(name);h.meta.setText(o.optString("type","movie").equals("series")?"مسلسل":"فيلم");Glide.with(MainActivity.this).load(poster).placeholder(shape(PANEL,dp(12))).centerCrop().into(h.img);h.itemView.setOnClickListener(v->details(o));}public int getItemCount(){return list.size();}}
    private void details(JSONObject item){String id=item.optString("id");new AlertDialog.Builder(this).setTitle(item.optString("title","CifliX")).setMessage("العنوان جاهز في مكتبة CifliX. سيتم فتح تفاصيله ومصادر المشاهدة بعد ربط خدمة البيانات.").setPositiveButton("حسناً",null).show();}
    @Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
}
