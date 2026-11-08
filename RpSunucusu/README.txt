RpSunucusu - Mafya / Polis / Kacakci RP eklentisi (Paper 1.20.x, Java 17)

DERLEME
  1) Java 17 + Maven kur
  2) Klasorde:  mvn clean package
  3) target/RpSunucusu-1.0.jar dosyasini sunucunun plugins/ klasorune at
  4) Sunucuyu baslat

KURULUM (oyunda, OP olarak)
  /rpadmin setnokta tedarik   -> paket alinacak yerde dur
  /rpadmin setnokta satis     -> paket satilacak yerde dur
  /rpadmin setnokta hapis     -> hapishane icinde dur

OYUNCU KOMUTLARI
  /register /login /meslek /rank /para [gonder <oyuncu> <miktar>]
  Kacakci: /paket  /sat
  Polis:   /ara <oyuncu>  /tutukla <oyuncu> <dk>  /serbest <oyuncu> (Baskomiser+)
  Mafya:   /harac <oyuncu>
  Madenci: cevher kirinca otomatik para+XP

RUTBELER (XP: 0 / 100 / 300 / 700 / 1500)
  Mafya:   Cirak > Asker > Kaptan > Alt Patron > Mafya Babasi
  Polis:   Stajyer > Memur > Komiser > Baskomiser > Emniyet Muduru
  Kacakci: Acemi > Tasiyici > Tedarikci > Baron > Kartel Lideri
  Madenci: Cirak > Usta > Ocak Basi > Muhendis > Maden Patronu

Ayarlar: plugins/RpSunucusu/config.yml

GITHUB ILE DERLEME (bilgisayara bir sey kurmadan)
  1) github.com'da ucretsiz hesap ac, "New repository" ile bos bir depo olustur
  2) "uploading an existing file" ile bu klasorun ICINDEKI her seyi yukle
     (pom.xml, src, .github klasoru ve README)
  3) Ustteki "Actions" sekmesine gir, "Build" calismasinin yesil olmasini bekle
  4) Calismaya tikla, en altta "Artifacts" kismindan RpSunucusu'yu indir
  5) Zip'in icinden cikan RpSunucusu-1.0.jar dosyasini sunucunun plugins klasorune at

YENI MESLEKLER
  Esnaf:   /teklif <oyuncu> <fiyat> (elindeki esyayi satar)  -> alici /kabul veya /reddet
  Doktor:  /iyilestir <oyuncu>  (100$, 10 sn bekleme)
  Avukat:  /savun <oyuncu>  (tutuklunun cezasini kisaltir, 150$)
  Emlakci: /teklif <oyuncu> <fiyat>  (Tapu Senedi satar)
  Market:  /market    Polis telsizi: kacakci /paket alinca polise haber gider

YENI SISTEMLER
  TAB listesi: oyuncu isminin onunde meslek yazar, en iyi meslekten en kotuye siralanir
               (sira Hud.java icindeki jobOrder: Polis > Doktor > Avukat > Mafya > Emlakci > Esnaf > Kacakci > Madenci > Vatandas)
  /banka [yatir|cek <miktar|hepsi>]   Olunce cuzdanin %10'u gider, banka guvende
  /ceza <oyuncu> <miktar>            Polis para cezasi
  /soygun                            Soygun noktasinda 30 sn dur, polise haber gider
  /ev ...                            Emlakci mulk olusturur, oyuncu satin alir, koruma otomatik
  /cete ...  /cs <mesaj>             Mafya cetesi, ortak kasa, cete sohbeti
  /g <mesaj> global sohbet, normal sohbet 40 blok, /telsiz (polis), /me, /do, /kimlik
  Meslek degistirme bekleme suresi: 5 dakika
ADMIN NOKTALARI
  /rpadmin setnokta soygun | hastane | banka  (ayrica tedarik, satis, hapis)
