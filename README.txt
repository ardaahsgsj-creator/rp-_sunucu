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
