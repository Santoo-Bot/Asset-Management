import datetime as dt
import io
import json
import sqlite3

import pytest

from asetga.core import backup, calc, fmt, security
from asetga.core.assets import AssetFilter, AssetInput
from asetga.core.db import Database, file_user_version
from asetga.core.models import WRITE_OFF_ID, AppError, AssetCondition, MasterType, Role, normalize_accessories


# ------------------------------------------------------------------ format & hitungan
def test_format_indonesia():
    assert fmt.rupiah(1_250_000) == "Rp 1.250.000"
    assert fmt.rupiah(-5000) == "-Rp 5.000"
    assert fmt.date(dt.date(2025, 1, 5)) == "05 Jan 2025"
    assert fmt.date_numeric(dt.date(2025, 1, 5)) == "05/01/2025"
    assert fmt.parse_rupiah("Rp 1.250.000") == 1_250_000
    assert fmt.percent(12.5) == "12,5%"
    assert fmt.to_epoch_day(dt.date(1970, 1, 2)) == 1
    assert fmt.from_epoch_day(19_723) == dt.date(2024, 1, 1)


def test_penyusutan_garis_lurus_sama_dengan_android():
    d = calc.depreciation(15_000_000, dt.date(2024, 1, 15), 4, 0.0, dt.date(2026, 11, 27))
    assert d.months_elapsed == 34
    assert d.annual_depreciation == 3_750_000
    assert d.accumulated == 10_625_000
    assert d.book_value == 4_375_000
    # Melewati umur ekonomis: tidak melebihi nilai yang dapat disusutkan.
    full = calc.depreciation(1_000_000, dt.date(2010, 1, 1), 4, 10.0, dt.date(2026, 1, 1))
    assert full.accumulated == 900_000 and full.book_value == 100_000 and full.fully_depreciated
    # Bulan penuh: 31 Jan → 28 Feb belum genap satu bulan.
    assert calc.depreciation(1200, dt.date(2025, 1, 31), 1, 0, dt.date(2025, 2, 28)).months_elapsed == 0


def test_kode_aset_berikutnya():
    assert calc.next_sequence("IT", 2024, ["IT-2024-001", "it-2024-007", "IT-2023-099", "FUR-2024-050"]) == 8
    assert calc.format_asset_code("it", 2024, 8) == "IT-2024-008"


def test_kelengkapan_dinormalisasi():
    assert normalize_accessories(" Charger ;casing, charger\nTas ") == "Charger, casing, Tas"
    assert normalize_accessories("  ") is None


# ------------------------------------------------------------------ keamanan
def test_hash_kata_sandi_format_android():
    h = security.hash_password("Rahasia123", iterations=10_000)
    assert h.startswith("pbkdf2-sha256$10000$")
    assert security.verify_password("Rahasia123", h)
    assert not security.verify_password("salah", h)
    assert not security.verify_password("x", "rusak")
    # Hash buatan Java (PBKDF2WithHmacSHA256, aplikasi Android) untuk "Rahasia123".
    java = "pbkdf2-sha256$120000$f5BRIwhiYPvM21dXwR3LKQ==$YUTOlIdApJEqMm01yju7SyYmt/URqtxmb+QA2pHwtok="
    assert security.verify_password("Rahasia123", java)


def test_enkripsi_backup_bolak_balik_dan_passphrase_salah(tmp_path):
    data = bytes(range(256)) * 5000
    enc = tmp_path / "x.gabak"
    with open(enc, "wb") as out:
        security.encrypt_file(io.BytesIO(data), out, "passphrase-uji", iterations=10_000)
    raw = enc.read_bytes()
    assert raw[:8] == b"ASETGABK" and raw[8] == 1 and int.from_bytes(raw[9:13], "big") == 10_000
    buf = io.BytesIO()
    security.decrypt_file(str(enc), buf, "passphrase-uji")
    assert buf.getvalue() == data
    with pytest.raises(security.WrongPassphraseError):
        security.decrypt_file(str(enc), io.BytesIO(), "passphrase-salah")
    other = tmp_path / "bukan.gabak"
    other.write_bytes(b"bukan backup sama sekali, cukup panjang untuk header")
    with pytest.raises(security.NotABackupError):
        security.decrypt_file(str(other), io.BytesIO(), "passphrase-uji")


# ------------------------------------------------------------------ database
def test_database_baru_identik_dengan_skema_room(tmp_path):
    db = Database(tmp_path / "aset_ga.db")
    schema = json.loads(db.schema.__class__.load.__defaults__[0].read_text(encoding="utf-8"))["database"]
    assert db.scalar("PRAGMA user_version") == schema["version"]
    assert db.scalar("SELECT identity_hash FROM room_master_table WHERE id = 42") == schema["identityHash"]
    tables = {r[0] for r in db.q("SELECT name FROM sqlite_master WHERE type = 'table'")}
    assert {e["tableName"] for e in schema["entities"]} <= tables
    # Kolom tiap tabel sama persis (nama & urutan) dengan Room.
    for e in schema["entities"]:
        cols = [r["name"] for r in db.q(f"PRAGMA table_info(`{e['tableName']}`)")]
        assert cols == [f["columnName"] for f in e["fields"]], e["tableName"]
    assert [r["id"] for r in db.q("SELECT id FROM asset_statuses ORDER BY sortOrder")] == \
        ["DIGUNAKAN", "TERSEDIA", "DALAM_PERBAIKAN", "PROPOSE_WRITE_OFF", WRITE_OFF_ID]
    assert db.scalar("SELECT COUNT(*) FROM categories") == 7
    db.close()
    # Dibuka ulang tanpa galat & tanpa seed ganda.
    db2 = Database(tmp_path / "aset_ga.db")
    assert db2.scalar("SELECT COUNT(*) FROM categories") == 7
    db2.close()


def test_database_versi_lain_ditolak(tmp_path):
    from asetga.core.db import SchemaMismatchError

    p = tmp_path / "aset_ga.db"
    Database(p).close()
    c = sqlite3.connect(p)
    c.execute("PRAGMA user_version = 12")
    c.close()
    with pytest.raises(SchemaMismatchError):
        Database(p)


# ------------------------------------------------------------------ pengguna & peran
def test_login_dan_pembatasan_percobaan(env):
    env.session.logout()
    assert env.users.authenticate("ADMIN ", "Rahasia123").role is Role.ADMIN
    for _ in range(5):
        with pytest.raises(AppError, match="salah"):
            env.users.authenticate("admin", "keliru")
    with pytest.raises(AppError, match="Terlalu banyak percobaan"):
        env.users.authenticate("admin", "Rahasia123")


def test_peran_diperiksa_di_service(env):
    env.users.create("viewer", "Vira", Role.VIEWER, "Rahasia123")
    env.session.login(env.users.authenticate("viewer", "Rahasia123"))
    with pytest.raises(AppError, match="tidak memiliki akses"):
        env.masters.save_location("", "Gudang", None)
    with pytest.raises(AppError, match="tidak memiliki akses"):
        env.assets.save(AssetInput(name="X"))


def test_admin_terakhir_tidak_bisa_dihapus_atau_diturunkan(env):
    me = env.session.user
    with pytest.raises(AppError):
        env.users.update(me.id, me.full_name, Role.STAFF, True)
    staf = env.users.create("staf", "Staf Satu", Role.STAFF, "Rahasia123")
    env.users.delete(staf.id)
    assert env.users.get(staf.id) is None
    assert env.db.scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType = 'USER' AND action = 'DELETE'") == 1


# ------------------------------------------------------------------ master data
def test_master_hapus_hanya_bila_tidak_dipakai_dan_write_off_dilindungi(env):
    loc = env.masters.save_location("", "Kantor Pusat", None)
    env.assets.save(AssetInput(name="Meja", category_id=env.category("FUR"), acquisition_date=dt.date(2024, 1, 1),
                               acquisition_cost=1_000_000, location_id=loc), qr_same_as_code=True)
    with pytest.raises(AppError, match="dipakai 1 aset"):
        env.masters.delete(MasterType.LOCATION, loc)
    with pytest.raises(AppError, match="status sistem"):
        env.masters.delete(MasterType.STATUS, WRITE_OFF_ID)
    env.masters.save_status(WRITE_OFF_ID, "Dihapusbukukan")  # nama boleh diubah
    assert env.masters.status_names()[WRITE_OFF_ID] == "Dihapusbukukan"
    unit = env.masters.list(MasterType.UNIT)[0]
    env.masters.delete(MasterType.UNIT, unit["id"])
    assert env.masters.get(MasterType.UNIT, unit["id"]) is None


def test_departemen_pindah_divisi_ikut_memindahkan_aset(env):
    d1 = env.masters.save_simple(MasterType.DIVISION, "", "Operasional")
    d2 = env.masters.save_simple(MasterType.DIVISION, "", "Keuangan")
    dep = env.masters.save_department("", "GA", d1)
    loc = env.masters.save_location("", "Kantor", None)
    aid = env.assets.save(AssetInput(name="Kursi", category_id=env.category("FUR"), acquisition_date=dt.date(2024, 1, 1),
                                     acquisition_cost=500_000, location_id=loc, department_id=dep), qr_same_as_code=True)
    assert env.assets.get(aid).division_id == d1
    env.masters.save_department(dep, "GA", d2)
    assert env.assets.get(aid).division_id == d2
    with pytest.raises(AppError) as e:
        env.masters.save_department("", "Tanpa Divisi", None)
    assert e.value.fields == {"division": "Pilih divisi induk"}


def test_kategori_validasi(env):
    with pytest.raises(AppError) as e:
        env.masters.save_category("", "Elektronik & IT", "it", 0, 120.0)
    assert set(e.value.fields) == {"name", "codePrefix", "usefulLifeYears", "residualPercent"}


# ------------------------------------------------------------------ aset
@pytest.fixture
def loc(env):
    return env.masters.save_location("", "Kantor Pusat", None)


def test_simpan_aset_kode_otomatis_dan_riwayat_hanya_field_berubah(env, loc):
    aid = env.assets.save(AssetInput(name="Laptop", category_id=env.category(), acquisition_date=dt.date(2024, 1, 15),
                                     acquisition_cost=15_000_000, location_id=loc, accessories="Charger, charger , Tas"),
                          qr_same_as_code=True)
    row = env.assets.get_row(aid)
    assert row["code"] == "IT-2024-001" and row["qrCode"] is None and row["accessories"] == "Charger, Tas"
    from dataclasses import replace

    env.assets.save(replace(env.assets.get(aid), name="Laptop Dell", acquisition_cost=16_000_000))
    log = env.db.one("SELECT * FROM audit_logs WHERE entityId = ? AND action = 'UPDATE'", (aid,))
    assert json.loads(log["changesJson"]) == {"Nama": ["Laptop", "Laptop Dell"], "Harga perolehan": ["Rp 15.000.000", "Rp 16.000.000"]}
    # Kode berikutnya untuk prefix & tahun yang sama.
    a2 = env.assets.save(AssetInput(name="Laptop 2", category_id=env.category(), acquisition_date=dt.date(2024, 5, 1),
                                    acquisition_cost=1, location_id=loc), qr_same_as_code=True)
    assert env.assets.get_row(a2)["code"] == "IT-2024-002"


def test_kode_qr_bentrok_ditolak(env, loc):
    base = dict(category_id=env.category(), acquisition_date=dt.date(2024, 1, 1), acquisition_cost=1, location_id=loc)
    env.assets.save(AssetInput(code="IT-2024-010", qr_code="QR-1", name="A", **base))
    with pytest.raises(AppError) as e:
        env.assets.save(AssetInput(code="IT-2024-011", qr_code="it-2024-010", name="B", **base))
    assert "qrCode" in e.value.fields
    with pytest.raises(AppError) as e:
        env.assets.save(AssetInput(code="QR-1", qr_code="X", name="C", **base))
    assert "code" in e.value.fields
    assert env.assets.find_by_code("qr-1")["code"] == "IT-2024-010"


def test_validasi_aset(env, loc):
    with pytest.raises(AppError) as e:
        env.assets.save(AssetInput(code="!!", name="", acquisition_date=dt.date(2030, 1, 1), acquisition_cost=-1, qr_code=""))
    assert {"code", "name", "category", "location", "acquisitionDate", "acquisitionCost", "qrCode"} <= set(e.value.fields)


def test_write_off_menghentikan_penyusutan(env, loc):
    aid = env.assets.save(AssetInput(name="Printer", category_id=env.category(), acquisition_date=dt.date(2023, 1, 1),
                                     acquisition_cost=4_800_000, location_id=loc), qr_same_as_code=True)
    env.assets.write_off(aid, dt.date(2024, 1, 1), 100_000, "Rusak")
    d = env.assets.detail(aid)
    assert d.row["status"] == WRITE_OFF_ID
    assert d.dep.months_elapsed == 12 and d.dep.accumulated == 1_200_000
    with pytest.raises(AppError, match="sudah berstatus Write Off"):
        env.assets.write_off(aid, dt.date(2024, 1, 2), None)
    # Write Off tetap tampil di daftar; bisa disaring.
    assert len(env.assets.list(AssetFilter())) == 1
    assert len(env.assets.list(AssetFilter(exclude_write_off=True))) == 0


def test_hapus_aset_permanen_dengan_alasan(env, loc):
    aid = env.assets.save(AssetInput(name="Meja", category_id=env.category("FUR"), acquisition_date=dt.date(2024, 1, 1),
                                     acquisition_cost=1, location_id=loc), qr_same_as_code=True)
    with pytest.raises(AppError, match="Alasan"):
        env.assets.delete([aid], " ")
    assert env.assets.delete([aid, aid], "Salah input") == (1, [])
    assert env.assets.get_row(aid) is None
    assert "Salah input" in env.db.scalar("SELECT summary FROM audit_logs WHERE entityId = ? AND action = 'DELETE'", (aid,))


def test_filter_dan_cari(env, loc):
    base = dict(acquisition_date=dt.date(2024, 1, 1), location_id=loc)
    env.assets.save(AssetInput(name="Laptop Dell", category_id=env.category(), acquisition_cost=10, serial_number="SN-77", **base), True)
    env.assets.save(AssetInput(name="Meja Kayu", category_id=env.category("FUR"), acquisition_cost=20,
                               condition=AssetCondition.RUSAK, **base), True)
    assert [r["name"] for r in env.assets.list(AssetFilter(query="sn-77"))] == ["Laptop Dell"]
    assert [r["name"] for r in env.assets.list(AssetFilter(condition="RUSAK"))] == ["Meja Kayu"]
    assert [r["name"] for r in env.assets.list(AssetFilter(sort="COST_DESC"))] == ["Meja Kayu", "Laptop Dell"]


# ------------------------------------------------------------------ Excel
def test_template_import_export_bolak_balik(env, loc, tmp_path):
    from asetga.core.excel import COLS, read_first_sheet

    t = tmp_path / "template.xlsx"
    env.excel.write_template(t)
    sheet = read_first_sheet(t)
    assert sheet[0] == [c.template_header for c in COLS]
    assert sheet[0][-2:] == ["Kelengkapan (pisahkan dengan koma)", "Vendor"]  # kolom baru selalu di akhir
    p = env.excel.preview(t)
    assert [r.errors for r in p.rows] == [[]]
    res = env.excel.commit(p, env.audit)
    assert res["created"] == 1
    a = env.assets.find_by_code(sheet[1][0])
    assert a["acquisitionCost"] == 15_000_000 and a["accessories"] == "Charger, Tas laptop"

    out = tmp_path / "export.xlsx"
    assert env.excel.export_xlsx(out) == 1
    again = env.excel.preview(out)
    assert len(again.unchanged) == 1 and not again.invalid


def test_import_perbarui_sel_kosong_berarti_nilai_lama(env, loc, tmp_path):
    from openpyxl import Workbook

    env.assets.save(AssetInput(code="IT-2024-001", qr_code="IT-2024-001", name="Laptop", category_id=env.category(),
                               acquisition_date=dt.date(2024, 1, 1), acquisition_cost=5_000_000, location_id=loc, notes="awal"))
    wb = Workbook()
    ws = wb.active
    ws.append(["Kode Aset*", "Nama Aset*", "Catatan", "Harga Perolehan*"])
    ws.append(["it-2024-001", "Laptop Baru", None, "Rp 6.500.000"])
    ws.append(["IT-2024-002", "Aset baru tanpa kolom wajib", None, None])
    f = tmp_path / "upd.xlsx"
    wb.save(f)
    p = env.excel.preview(f)
    upd = next(r for r in p.rows if r.row_number == 2)
    assert upd.is_valid and upd.input.name == "Laptop Baru" and upd.input.notes == "awal" and upd.input.acquisition_cost == 6_500_000
    new = next(r for r in p.rows if r.row_number == 3)
    assert not new.is_valid and "memerlukan kolom" in new.errors[0]


def test_import_baris_bermasalah_dilaporkan(env, loc, tmp_path):
    from openpyxl import Workbook

    from asetga.core.excel import COLS

    wb = Workbook()
    ws = wb.active
    ws.append([c.template_header for c in COLS])
    row = {c.name: "" for c in COLS}
    row.update(CODE="IT-2024-050", QR="IT-2024-050", NAME="Proyektor", CATEGORY="tidak ada", ACQ_DATE="31/02/2024", COST="abc",
               LOCATION="Kantor Pusat", CONDITION="Cukup", STATUS="Aktif")
    ws.append([row[c.name] for c in COLS])
    ws.append([row[c.name] for c in COLS])
    f = tmp_path / "salah.xlsx"
    wb.save(f)
    p = env.excel.preview(f)
    errs = p.rows[0].errors
    assert any("Kategori" in e for e in errs) and any("Tgl perolehan" in e for e in errs) and any("Harga" in e for e in errs)
    assert any("ganda di file" in e for e in p.rows[1].errors)


# ------------------------------------------------------------------ backup
def test_backup_restore_bolak_balik(env, loc, tmp_path):
    aid = env.assets.save(AssetInput(name="Laptop", category_id=env.category(), acquisition_date=dt.date(2024, 1, 1),
                                     acquisition_cost=1, location_id=loc), qr_same_as_code=True)
    photos = env.dir / "photos"
    photos.mkdir(exist_ok=True)
    (photos / "a.jpg").write_bytes(b"foto")
    env.db.exec("UPDATE assets SET photoPath = 'photos/a.jpg' WHERE id = ?", (aid,))
    target = tmp_path / "out" / "b.gabak"
    target.parent.mkdir()
    with pytest.raises(AppError, match="minimal"):
        backup.create_backup(env.db, env.dir, target, "pendek")
    backup.create_backup(env.db, env.dir, target, "passphrase-uji", env.audit)

    with pytest.raises(AppError, match="Passphrase salah"):
        backup.prepare_restore(target, "salah-salah", env.db.schema)

    # Restore ke folder lain yang sudah berisi data berbeda.
    other = tmp_path / "laptop2"
    other.mkdir()
    db2 = Database(other / "aset_ga.db")
    prepared = backup.prepare_restore(target, "passphrase-uji", env.db.schema)
    assert prepared.user_count() == 1
    backup.apply_restore(prepared, other, db2)
    db3 = Database(other / "aset_ga.db")
    assert db3.scalar("SELECT COUNT(*) FROM assets") == 1
    assert (other / "photos" / "a.jpg").read_bytes() == b"foto"
    assert db3.scalar("SELECT COUNT(*) FROM audit_logs WHERE summary = 'Backup data dibuat'") == 1
    db3.close()


def test_restore_versi_skema_berbeda_ditolak(env, tmp_path):
    import zipfile

    raw = tmp_path / "old.db"
    c = sqlite3.connect(raw)
    c.execute("CREATE TABLE t(a)")
    c.execute("PRAGMA user_version = 12")
    c.commit()
    c.close()
    assert file_user_version(raw) == 12
    z = tmp_path / "z.zip"
    with zipfile.ZipFile(z, "w") as zf:
        zf.write(raw, backup.DB_ENTRY)
        zf.writestr("../jahat.txt", "x")
    enc = tmp_path / "old.gabak"
    with open(z, "rb") as src, open(enc, "wb") as out:
        security.encrypt_file(src, out, "passphrase-uji", iterations=10_000)
    with pytest.raises(AppError, match="versi lama"):
        backup.prepare_restore(enc, "passphrase-uji", env.db.schema)
    assert not (tmp_path / "jahat.txt").exists()


def test_backup_buatan_desktop_untuk_test_android_masih_cocok():
    """File uji di app/src/test/resources harus dibuat dari skema yang sama (jalankan tools/make_android_fixture.py)."""
    from pathlib import Path

    from asetga.core.db import RoomSchema

    fixture = Path(__file__).resolve().parents[2] / "app" / "src" / "test" / "resources" / "desktop-compat" / "desktop-backup.gabak"
    if not fixture.exists():
        pytest.skip("repo Android tidak tersedia")
    prepared = backup.prepare_restore(fixture, "passphrase-uji", RoomSchema.load())
    prepared.cleanup()
