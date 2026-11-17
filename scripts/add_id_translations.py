#!/usr/bin/env python3
"""Sisipkan terjemahan Indonesia ke file values-id yang cocok.

Sumber kebenaran: file EN (values/). Setiap kunci yang diterjemahkan di bawah
disisipkan ke file values-id dengan nama file yang sama, jadi struktur resource
tetap konsisten. Placeholder %1$s / %2$d dipertahankan persis.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "composeApp/src/commonMain/composeResources/values"
TARGET = ROOT / "composeApp/src/commonMain/composeResources/values-id"

TRANSLATIONS: dict[str, str] = {
    # --- compose / auth / settings ---
    "compose_settings_page_media_servers": "Server media",
    "compose_settings_page_media_server": "Server",
    "compose_auth_terms_prefix": "Dengan mendaftar, saya menyetujui",
    "compose_auth_terms_link": "Ketentuan",
    "compose_auth_link_cancel": "Batal",
    "compose_auth_link_code_expired": "Kode ini sudah kedaluwarsa.",
    "compose_auth_link_creating_code": "Membuat kode…",
    "compose_auth_link_failed": "Gagal masuk lewat tautan.",
    "compose_auth_link_open": "Buka HOFLIX.tv/link",
    "compose_auth_link_open_failed": "Tautan tidak bisa dibuka.",
    "compose_auth_link_sign_in": "Masuk lewat tautan",
    "compose_auth_link_signing_in": "Sedang masuk…",
    "compose_auth_link_try_again": "Coba lagi",
    "compose_auth_link_waiting": "Menunggu persetujuan…",
    "compose_catalog_subtitle_simkl_library": "Daftar tonton Simkl",
    "compose_player_audio_delay": "Jeda audio",
    "compose_player_audio_amplification": "Penguatan audio",
    "compose_player_audio_amplification_keep": "Simpan untuk video berikutnya",
    "compose_player_center_mix": "Level campuran tengah",
    "compose_player_languages": "Bahasa",
    "compose_player_no_subtitle_lines_found": "Tidak ada baris subtitle",
    "compose_player_subtitle_lines_load_error": "Baris subtitle gagal dimuat",
    "compose_player_chapters": "Bab",
    "compose_player_panel_chapters": "Bab (%1$d)",
    "compose_player_chapter_number": "Bab %1$d",
    "compose_player_chapter_remaining": "Sisa %1$s",
    "compose_settings_page_privacy_policy": "Kebijakan privasi",
    "compose_settings_page_streams": "Sumber",
    "compose_settings_page_tracking": "Pelacakan",
    "compose_settings_root_advanced_section": "LANJUTAN",
    "compose_settings_root_privacy_policy_description": "Pelajari bagaimana HOFLIX menangani datamu",
    "compose_settings_root_streams_description": "Tampilan hasil sumber dan aturan URL lencana.",
    "compose_settings_root_tracking_description": "Hubungkan penyedia pelacakan dan pilih yang mengisi Perpustakaan serta Lanjutkan Menonton.",
    "compose_tracking_list_picker_loading": "Memuat daftar…",
    "compose_tracking_list_picker_subtitle": "Pilih tempat menyimpan judul ini",
    "compose_player_episode_code": "S%1$d E%2$d",
    "compose_player_more_actions": "Kontrol lainnya",
    "compose_player_fewer_actions": "Lebih sedikit kontrol",
    "compose_player_speed": "Kecepatan putar",
    "compose_player_show_elapsed_time": "Tampilkan waktu berjalan",
    "compose_player_show_remaining_time": "Tampilkan sisa waktu",
    "compose_player_brightness": "Kecerahan",
    "compose_player_volume": "Volume",
    "compose_settings_root_about_description": "Info aplikasi, pembaruan, dan ucapan terima kasih",
    # --- theme / action / layout ---
    "theme_gold": "Emas",
    "theme_jade": "Giok",
    "theme_rose_gold": "Emas Mawar",
    "theme_arctic_blue": "Biru Arktik",
    "theme_graphite": "Grafit",
    "theme_custom": "Kustom",
    "action_collapse": "Ciutkan",
    "action_expand": "Bentangkan",
    "action_delete_confirm_message": "Yakin ingin menghapusnya?",
    "action_delete_confirm_title": "Hapus?",
    "action_remove_anyway": "Tetap hapus",
    "action_switch": "Ganti",
    "action_support_nuvio": "Dukung HOFLIX",
    "layout_catalog_type": "Tampilkan jenis katalog",
    "layout_catalog_type_sub": "Tampilkan akhiran jenis di samping nama katalog (Film/Serial).",
    "layout_overall_ratings": "Rating keseluruhan",
    "layout_overall_ratings_sub_on": "Rating standar dan TMDB ditampilkan.",
    "layout_overall_ratings_sub_off": "Rating standar dan TMDB disembunyikan. Pengaturan penyedia MDBList lebih diutamakan di halaman detail.",
    "layout_episode_ratings": "Rating episode",
    "layout_episode_ratings_sub": "Pilih rating episode mana yang terlihat.",
    "layout_ratings_show": "Tampilkan",
    "layout_ratings_hide": "Sembunyikan",
    "layout_ratings_hide_unwatched": "Sembunyikan yang belum ditonton",
    "layout_random_episode_sub": "Acak episode dari serial mana pun. Mematikan ini menghentikan pengacakan.",
    # --- downloads ---
    "downloads_open_directory_failed": "Folder unduhan tidak bisa dibuka",
    "downloads_error_finalize_failed": "Gagal menyelesaikan berkas unduhan",
    "downloads_error_open_partial_failed": "Gagal membuka berkas unduhan sementara",
    "downloads_error_partial_not_open": "Berkas unduhan sementara belum dibuka",
    "downloads_error_write_partial_failed": "Gagal menulis berkas unduhan sementara",
    "downloads_error_finalize_file_failed": "Gagal menyelesaikan berkas unduhan",
    "downloads_error_open_partial_file_failed": "Gagal membuka berkas unduhan sementara",
    "downloads_error_partial_file_not_open": "Berkas unduhan sementara belum dibuka",
    "downloads_error_write_partial_file_failed": "Gagal menulis berkas unduhan sementara",
    # --- player ---
    "player_seek_position": "Posisi pemutaran",
    "player_auto_skip_intro_notification": "Intro dilewati ke %1$s",
    "player_auto_skip_recap_notification": "Rekap dilewati ke %1$s",
    "player_auto_skip_outro_notification": "Penutup dilewati ke %1$s",
    "player_auto_skip_movie_credits_notification": "Kredit dilewati ke %1$s",
    "player_loading_preparing": "Menyiapkan sumber…",
    "player_loading_subtitles": "Mengambil subtitle…",
    "player_loading_subtitles_from": "Mengambil subtitle dari %1$d addon…",
    "player_loading_subtitles_progress": "Mengambil subtitle… (%1$d / %2$d)",
    "player_loading_subtitles_addon": "Subtitle: %1$s (%2$d / %3$d)",
    "player_loading_building": "Menyiapkan pemutar…",
    "player_loading_starting": "Memulai pemutaran…",
    "player_loading_buffering": "Memuat…",
    "player_engine_unavailable_rebuild": "Mesin pemutar MPV tidak tersedia. Bangun ulang aplikasi.",
    "player_error_mpv_unavailable": "Mesin pemutar MPV tidak tersedia. Bangun ulang aplikasi.",
    "player_error_unable_to_play_stream": "Sumber ini tidak bisa diputar.",
    "player_action_submit_intro": "Kirim intro",
    "player_action_video_settings": "Pengaturan video",
    "player_video_settings_title": "Video",
    "player_video_settings_reset_tuning": "Setel ulang penyesuaian",
    "player_video_settings_output_preset": "Preset keluaran",
    "player_video_settings_hdr_peak_detection": "Deteksi puncak HDR",
    "player_video_settings_hdr_peak_detection_desc": "Perkirakan kecerahan puncak HDR saat metadata buruk atau tidak ada.",
    "player_video_settings_tone_mapping": "Pemetaan tone",
    "player_video_settings_deband": "Hilangkan banding",
    "player_video_settings_deband_desc": "Kurangi banding warna dengan sedikit biaya performa.",
    "player_video_settings_interpolation": "Interpolasi frame",
    "player_video_settings_interpolation_desc": "Gerakan lebih halus saat mpv bisa memakai sinkronisasi layar dengan bersih.",
    "player_video_settings_brightness": "Kecerahan",
    "player_video_settings_contrast": "Kontras",
    "player_video_settings_saturation": "Saturasi",
    "player_video_settings_gamma": "Gamma",
    "player_ios_preset_native_edr_label": "EDR asli",
    "player_ios_preset_native_edr_desc": "Terbaik untuk iPhone dan iPad yang mendukung HDR.",
    "player_ios_preset_sdr_tone_mapped_label": "SDR dipetakan tone",
    "player_ios_preset_sdr_tone_mapped_desc": "Putih dan hitam lebih terprediksi pada keluaran gaya SDR.",
    "player_ios_preset_compatibility_label": "Kompatibilitas",
    "player_ios_preset_compatibility_desc": "Paling dekat dengan perilaku MPV iOS versi lama.",
    "player_ios_preset_custom_label": "Kustom",
    "player_ios_preset_custom_desc": "Pakai nilai lanjutan di bawah.",
    "player_ios_hardware_decoder_off": "Mati",
    "player_addon_subtitle_display_format": "%1$s (%2$s)",
    "player_torrent_peer_info": "%1$d seed · %2$d peer",
    "player_torrent_buffered_status": "%1$s tersangga · %2$s · %3$s",
    "player_torrent_status": "%1$s · %2$s",
    "player_torrent_stats": "%1$d peer · %2$d seed · %3$d%%",
    "player_torrent_connecting_peers": "Menghubungkan ke peer…",
    "player_torrent_starting_engine": "Menyalakan mesin P2P…",
    "player_torrent_fetching_metadata": "Mengambil metadata torrent…",
    "player_torrent_preparing_stream": "Menyiapkan sumber torrent…",
    "player_torrent_connecting_status": "%1$s · %2$s · %3$s",
    "player_torrent_loading_status": "%1$s terunduh · %2$s · %3$s",
    "player_error_failed_start_torrent": "Gagal memulai torrent: %1$s",
    "player_error_torrent": "Kesalahan torrent: %1$s",
    "player_external_loading_subtitles": "Memuat subtitle dari addon…",
    "player_external_downloading_subtitles": "Mengunduh subtitle…",
    "player_skip_movie_credits": "Lewati kredit",
    "player_skip_to_post_credits": "Lewati ke adegan setelah kredit",
    "player_engine_switching_automatic_message": "Pemutaran gagal — beralih ke %1$s…",
    "player_engine_switch_title": "Beralih ke %1$s?",
    "player_engine_switch_message": "Video akan dimuat ulang dari posisi terakhir.",
    # --- subtitle appearance ---
    "subtitle_appearance": "Tampilan",
    "subtitle_preview": "Pratinjau",
    "subtitle_preview_text": "Setiap cerita dimulai dari rasa ingin tahu.",
    "subtitle_appearance_hint": "Berlaku untuk subtitle teks. Subtitle ASS bergaya dan subtitle gambar tetap memakai tampilan aslinya.",
    "subtitle_font": "Huruf",
    "subtitle_font_default": "Bawaan",
    "subtitle_font_sans": "Sans serif",
    "subtitle_font_serif": "Serif",
    "subtitle_font_mono": "Monospace",
    "subtitle_font_import": "Impor huruf",
    "subtitle_font_import_hint": "Pilih berkas .ttf atau .otf, maksimal 10 MB. Huruf impor tetap di perangkat ini.",
    "subtitle_font_import_error": "Huruf ini tidak bisa diimpor. Pilih berkas .ttf atau .otf yang sah di bawah 10 MB.",
    "subtitle_font_missing": "Huruf tidak tersedia di perangkat ini. Memakai huruf bawaan.",
    "subtitle_font_remove": "Hapus %1$s",
    "subtitle_background_opacity": "Opasitas latar",
    "subtitle_background_roundness": "Kebulatan sudut",
    "subtitle_shadow": "Bayangan teks",
    "subtitle_shadow_description": "Memisahkan teks dari adegan terang.",
    "subtitle_shadow_opacity": "Opasitas bayangan",
    "subtitle_shadow_size": "Ukuran bayangan",
    "subtitle_reset_appearance": "Setel ulang tampilan",
}

PARTY_TRANSLATIONS: dict[str, str] = {
    "party_start_title": "Tonton bersama",
    "party_start_description": "Ajak temanmu. Semua memutar dari sumbernya sendiri dan tetap sinkron.",
    "party_start_action": "Mulai nonton bareng",
    "party_sign_in_required": "Masuk ke akun HOFLIX untuk memakai nonton bareng.",
    "party_unavailable": "Nonton bareng sedang tidak tersedia.",
    "party_experimental": "Nonton bareng masih eksperimental dan bisa saja tidak berjalan sesuai harapan.",
    "party_code_label": "Kode party",
    "party_copy_invite": "Salin undangan",
    "party_invite_copied": "Undangan disalin",
    "party_invite_message": "Ikut nonton bareng HOFLIX-ku dengan kode %1$s\n%2$s",
    "party_seats": "%1$d dari %2$d kursi",
    "party_host": "Tuan rumah",
    "party_you": "Kamu",
    "party_status_ready": "Menonton",
    "party_status_buffering": "Memuat",
    "party_status_loading": "Memuat",
    "party_status_choosing": "Memilih sumber",
    "party_status_elsewhere": "Menonton hal lain",
    "party_status_offline": "Luring",
    "party_remove_title": "Keluarkan %1$s?",
    "party_remove_message": "Dia tidak bisa lagi bergabung ke party ini.",
    "party_leave": "Keluar dari party",
    "party_end": "Akhiri untuk semua",
    "party_end_title": "Akhiri nonton bareng?",
    "party_end_message": "Semua orang akan terputus dari party.",
    "party_now_watching": "Party sedang menonton %1$s",
    "party_switch": "Alihkan party ke %1$s",
    "party_switch_title": "Alihkan party?",
    "party_switch_message": "Semua orang akan mulai menonton %1$s bersamamu.",
    "party_switch_confirm": "Alihkan",
    "party_watch_with_party": "Tonton bersama party",
    "party_waiting_for": "Menunggu %1$s",
    "party_waiting": "Menunggu semua orang",
    "party_reconnecting": "Menyambung ulang",
    "party_member_joined": "%1$s bergabung ke nonton bareng",
    "party_member_left": "%1$s keluar dari nonton bareng",
    "party_removed": "Kamu dikeluarkan dari nonton bareng",
    "party_ended": "Nonton bareng sudah berakhir",
    "party_media_changed": "Party sekarang menonton %1$s",
    "party_join_title": "Gabung nonton bareng",
    "party_join_description": "Masukkan kode 6 karakter yang dibagikan temanmu.",
    "party_join_action": "Gabung",
    "party_error_not_found": "Tidak ada party dengan kode itu.",
    "party_error_full": "Party ini sudah penuh.",
    "party_error_removed": "Kamu tidak bisa bergabung ke party ini.",
    "party_error_ended": "Party ini sudah berakhir.",
    "party_error_rate_limited": "Terlalu banyak percobaan. Coba lagi beberapa menit.",
    "party_error_network": "Party tidak bisa dihubungi. Periksa koneksimu.",
    "party_source": "Sumber party",
    "party_episode_format": "S%1$d E%2$d",
    "party_host_only_title": "Hanya saya yang bisa mengatur pemutaran",
    "party_host_only_description": "Yang lain tetap bisa menjeda atau memundurkan untuk dirinya sendiri.",
    "party_host_only_note": "Hanya tuan rumah yang bisa mengatur pemutaran",
    "party_control_host_only": "Sekarang hanya tuan rumah yang bisa mengatur pemutaran",
    "party_control_everyone": "Sekarang semua orang bisa mengatur pemutaran",
    "party_paused_for_you": "Dijeda untukmu",
    "party_behind": "%1$s tertinggal",
    "party_behind_title": "Kamu tertinggal %1$s dari party",
    "party_catch_up": "Kejar",
    "party_caught_up": "Kembali sinkron dengan party",
    "party_action_paused": "%1$s menjeda",
    "party_action_resumed": "%1$s melanjutkan",
    "party_action_seeked": "%1$s melompat ke %2$s",
    "party_action_speed": "%1$s mengubah kecepatan ke %2$s",
    "party_joined_title": "Kamu sudah masuk",
    "party_hosted_by": "Tuan rumah: %1$s",
    "party_open_seat": "Undang teman",
    "party_host_hint": "Untuk menjadi tuan rumah, mulai party dari pemutar.",
    "party_share_title": "Bagikan sumberku",
    "party_share_description": "Teman bisa memutar tautanmu alih-alih memilih sendiri. Tautan bisa memuat kunci debridmu.",
    "party_share_unavailable": "Sumber ini tidak bisa dibagikan.",
    "party_shared_by": "Dibagikan oleh %1$s",
    "party_tab_party": "Party",
    "party_tab_chat": "Obrolan",
    "party_chat_empty_title": "Belum ada pesan",
    "party_chat_empty_message": "Hanya orang di party ini yang bisa melihat obrolan.",
    "party_chat_placeholder": "Pesan",
    "party_chat_send": "Kirim",
    "party_chat_not_sent": "Tidak terkirim. Ketuk untuk coba lagi",
    "party_chat_new_messages": "Pesan baru",
    "party_companion_chatting_as": "Mengobrol sebagai %1$s",
    "party_companion_seat_left": "Perangkat lainmu keluar dari nonton bareng.",
}


def escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def source_file_for(key: str) -> Path | None:
    for candidate in sorted(BASE.glob("strings*.xml")):
        text = candidate.read_text(encoding="utf-8")
        if f'name="{key}"' in text:
            return candidate
    return None


def apply(translations: dict[str, str]) -> tuple[int, int]:
    inserted = 0
    skipped = 0
    per_file: dict[Path, list[tuple[str, str]]] = {}
    for key, value in translations.items():
        src = source_file_for(key)
        if src is None:
            print(f"  ! kunci tidak ada di values: {key}", file=sys.stderr)
            skipped += 1
            continue
        per_file.setdefault(src, []).append((key, value))

    for src, items in per_file.items():
        dst = TARGET / src.name
        if not dst.exists():
            dst.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n', encoding="utf-8")
        text = dst.read_text(encoding="utf-8")
        additions = []
        for key, value in items:
            if f'name="{key}"' in text:
                skipped += 1
                continue
            additions.append(f'    <string name="{key}">{escape(value)}</string>')
            inserted += 1
        if not additions:
            continue
        block = "\n".join(additions)
        if "</resources>" in text:
            text = text.replace("</resources>", block + "\n</resources>", 1)
        else:
            text = text.rstrip() + "\n" + block + "\n"
        dst.write_text(text, encoding="utf-8")
    return inserted, skipped


if __name__ == "__main__":
    a, s = apply(TRANSLATIONS)
    b, t = apply(PARTY_TRANSLATIONS)
    print(f"disisipkan: {a + b}, dilewati: {s + t}")
