"""Tahap 2: mutasi, servis, stok, label QR."""

import datetime as dt
import json

import pytest

from asetga.core import fmt
from asetga.core.assets import AssetInput
from asetga.core.labels import LabelData, qr_matrix, render_png, write_pdf
from asetga.core.models import AppError, MasterType, Role, ServiceType, StockTxnType
from asetga.core.operations import OperationService, ServiceInput, service_due_status
from asetga.core.stock import ItemInput, StockService


@pytest.fixture
def ops(env):
    return OperationService(env.db, env.session, env.audit)


@pytest.fixture
def stock(env):
    return StockService(env.db, env.session, env.audit)


@pytest.fixture
def asset(env):
    loc = env.masters.save_location("", "Kantor Pusat", None)
    return env.assets.save(AssetInput(name="Laptop", category_id=env.category(), acquisition_date=dt.date(2024, 1, 15),
                                      acquisition_cost=15_000_000, location_id=loc, user_location_detail="Budi"), qr_same_as_code=True)


# ------------------------------------------------------------------ mutasi
def test_mutasi_memperbarui_aset_dan_riwayat(env, ops, asset):
    gudang = env.masters.save_location("", "Gudang", None)
    ops.record_mutation(asset, dt.date(2025, 3, 1), gudang, " Sari · Lt 2 ")
    a = env.assets.get_row(asset)
    assert a["locationId"] == gudang and a["userLocationDetail"] == "Sari · Lt 2"
    m = ops.mutations(asset)[0]
    assert m["fromLocation"] == "Kantor Pusat" and m["toLocation"] == "Gudang"
    assert m["fromUserLocation"] == "Budi" and m["toUserLocation"] == "Sari · Lt 2" and m["reason"] == ""
    log = env.db.one("SELECT * FROM audit_logs WHERE summary LIKE 'Mutasi aset%'")
    assert json.loads(log["changesJson"]) == {"Lokasi": ["Kantor Pusat", "Gudang"], "Pengguna / detail lokasi": ["Budi", "Sari · Lt 2"]}


def test_mutasi_tanpa_perubahan_atau_tanggal_salah_ditolak(env, ops, asset):
    loc = env.assets.get_row(asset)["locationId"]
    with pytest.raises(AppError) as e:
        ops.record_mutation(asset, dt.date(2025, 1, 1), loc, "Budi")
    assert e.value.fields == {"target": "Ubah lokasi atau pengguna / detail lokasi tujuan"}
    with pytest.raises(AppError) as e:
        ops.record_mutation(asset, dt.date(2023, 1, 1), loc, "Andi")
    assert "sebelum tanggal perolehan" in e.value.fields["date"]
    # Hanya pengguna yang berubah: boleh.
    ops.record_mutation(asset, dt.date(2025, 1, 1), loc, "Andi")


# ------------------------------------------------------------------ servis
def test_servis_simpan_ubah_dan_jadwal(env, ops, asset):
    with pytest.raises(AppError) as e:
        ops.save_service(ServiceInput(asset, service_date=dt.date(2025, 1, 1), description=" ", cost=-1,
                                      next_service_date=dt.date(2024, 12, 1)))
    assert set(e.value.fields) == {"description", "cost", "nextServiceDate"}
    sid = ops.save_service(ServiceInput(asset, service_date=dt.date(2026, 11, 1), type=ServiceType.KOREKTIF, cost=250_000,
                                        description="Ganti baterai", next_service_date=dt.date(2026, 12, 15)))
    sched = ops.schedules()
    assert len(sched) == 1 and sched[0][1] == "Jatuh tempo"
    row = ops.get_service(sid)
    ops.save_service(OperationService.service_input(row).__class__(**{**OperationService.service_input(row).__dict__, "cost": 300_000}))
    log = env.db.one("SELECT * FROM audit_logs WHERE entityType = 'SERVICE' AND action = 'UPDATE'")
    assert json.loads(log["changesJson"]) == {"Biaya": ["Rp 250.000", "Rp 300.000"]}
    assert env.db.scalar("SELECT COUNT(*) FROM audit_logs WHERE entityType = 'ASSET' AND summary LIKE 'Servis Korektif:%'") == 2
    assert [r["assetName"] for r in ops.services("baterai")] == ["Laptop"]
    # Aset Write Off tidak ikut jadwal.
    env.assets.write_off(asset, dt.date(2026, 11, 20), None)
    assert ops.schedules() == []


def test_status_jadwal_servis():
    t = dt.date(2026, 11, 27)
    assert service_due_status(None, t) == "Belum dijadwalkan"
    assert service_due_status(dt.date(2026, 11, 26), t) == "Terlambat"
    assert service_due_status(dt.date(2026, 12, 27), t) == "Jatuh tempo"
    assert service_due_status(dt.date(2026, 12, 28), t) == "Terjadwal"


# ------------------------------------------------------------------ stok
def _unit(env, name="Rim"):
    return next(u["id"] for u in env.masters.list(MasterType.UNIT) if u["name"] == name)


def test_stok_masuk_keluar_dan_reorder(env, stock):
    iid = stock.save_item(ItemInput(code="atk-001", barcode="899123", name="Kertas A4", unit_id=_unit(env), initial_stock="10",
                                    min_stock="5"))
    assert stock.item(iid)["code"] == "ATK-001"
    stock.record_txn(iid, StockTxnType.OUT, dt.date(2026, 11, 1), "6,5")
    assert stock.item(iid)["currentStock"] == pytest.approx(3.5)
    assert stock.reorder_count() == 1 and [i["code"] for i in stock.items(reorder_only=True)] == ["ATK-001"]
    with pytest.raises(AppError) as e:
        stock.record_txn(iid, StockTxnType.OUT, dt.date(2026, 11, 2), "4")
    assert e.value.fields["qty"] == "Stok tersedia 3,5 Rim"
    stock.record_txn(iid, StockTxnType.IN, dt.date(2026, 11, 3), "20", "PO-77")
    assert stock.reorder_count() == 0
    assert "stok 3,5 → 23,5" in env.db.scalar("SELECT summary FROM audit_logs WHERE entityType = 'STOCK' ORDER BY timestamp DESC, rowid DESC LIMIT 1")
    assert stock.find_by_code_or_barcode("899123")["id"] == iid
    with pytest.raises(AppError) as e:
        stock.save_item(ItemInput(code="ATK-001", barcode="899123", name="Ganda", unit_id=_unit(env)))
    assert set(e.value.fields) == {"code", "barcode"}
    # Satuan yang dipakai barang tidak bisa dihapus.
    with pytest.raises(AppError, match="1 barang stok"):
        env.masters.delete(MasterType.UNIT, _unit(env))
    stock.delete_item(iid)
    assert stock.item(iid) is None and env.db.scalar("SELECT COUNT(*) FROM stock_txns") == 0


def test_stok_hanya_admin_yang_hapus(env, stock):
    iid = stock.save_item(ItemInput(code="ATK-002", name="Pulpen", unit_id=_unit(env, "Pcs")))
    env.users.create("staf", "Staf", Role.STAFF, "Rahasia123")
    env.session.login(env.users.authenticate("staf", "Rahasia123"))
    with pytest.raises(AppError, match="Hanya Admin"):
        stock.delete_item(iid)
    stock.record_txn(iid, StockTxnType.IN, fmt.today(), "3")


def test_import_dan_export_stok(env, stock, tmp_path):
    from openpyxl import load_workbook

    from asetga.core.excel import commit_stock, export_stock, preview_stock, write_stock_template

    t = tmp_path / "barang.xlsx"
    write_stock_template(env.masters, t)
    p = preview_stock(stock, env.masters, t)
    assert [r.errors for r in p.rows] == [[]]
    assert commit_stock(stock, p, env.audit)["imported"] == 1
    again = preview_stock(stock, env.masters, t)
    assert any("sudah ada" in e for e in again.rows[0].errors)
    out = tmp_path / "stok.xlsx"
    assert export_stock(stock, out, "xlsx") == 1
    wb = load_workbook(out)
    assert wb.sheetnames == ["Stok", "Transaksi"] and wb["Stok"]["H2"].value == 20


# ------------------------------------------------------------------ label
def test_label_pdf_satu_halaman_per_label_ukuran_50x20(tmp_path):
    labels = [LabelData("IT-2024-001", "Elektronik & IT", "IT-2024-001"), LabelData("FUR-1", "Furnitur", "QR-ABC")]
    p = tmp_path / "label.pdf"
    write_pdf(labels, p)
    import re

    data = p.read_bytes()
    assert len(re.findall(rb"/Type /Page[^s]", data)) == 2
    assert re.findall(rb"/MediaBox \[ 0 0 ([0-9.]+) ([0-9.]+) \]", data)[0] == (b"141.7323", b"56.69291")  # 50 x 20 mm
    img = render_png(labels[0])
    assert img.size == (600, 240)
    m = qr_matrix("IT-2024-001")
    assert len(m) == 21 and m[0][0] and m[0][6] and not m[0][7]  # pola finder kiri atas
