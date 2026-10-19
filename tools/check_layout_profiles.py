#!/usr/bin/env python3
"""Static geometry checks for the responsive FCM Guard dashboard.

Android still performs the real resource/layout compilation in assembleDebug. These
checks guard the dimensions that are easiest to regress on compact HyperOS phones.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/res/values/dimens.xml"
LARGE = ROOT / "app/src/main/res/values-w420dp/dimens.xml"


def read_dp(path: Path):
    root = ET.parse(path).getroot()
    out = {}
    for item in root.findall("dimen"):
        raw = (item.text or "").strip()
        if raw.endswith("dp"):
            out[item.attrib["name"]] = float(raw[:-2])
    return out


base = read_dp(BASE)
large = {**base, **read_dp(LARGE)}

profiles = [
    ("narrow-320dp", 320),
    ("compact-360dp", 360),
    ("xiaomi17-class-393dp", 393),
    ("compact-411dp", 411),
    ("large-430dp", 430),
    ("ultra-class-480dp", 480),
]

for name, width in profiles:
    d = large if width >= 420 else base
    page = d["page_horizontal_padding"]
    hero = width - 2 * page
    inner = hero - 2 * d["hero_inner_padding"]
    title_space = inner - d["language_button_width"] - d["language_button_margin_start"]
    status = hero

    assert hero >= 285, f"{name}: hero too narrow ({hero}dp)"
    assert title_space >= 135, f"{name}: title/language row too tight ({title_space}dp)"
    assert status >= 265, f"{name}: status card too narrow ({status}dp)"

layout = ET.parse(ROOT / "app/src/main/res/layout/activity_main.xml").getroot()
android_id = "{http://schemas.android.com/apk/res/android}id"
android_width = "{http://schemas.android.com/apk/res/android}layout_width"
ids = {node.attrib.get(android_id) for node in layout.iter()}
for required in (
    "@+id/stickyHeader",
    "@+id/heroCard",
    "@+id/scroll",
):
    assert required in ids, f"missing required dashboard id: {required}"

assert not any(node.attrib.get(android_id) == "@+id/statusCard" for node in layout.iter()), (
    "statusCard must scroll with the dashboard content instead of floating in the fixed header"
)
content = ET.parse(ROOT / "app/src/main/res/layout/dashboard_scroll_content.xml").getroot()
first_content = list(content)[0]
assert first_content.tag == "include" and first_content.attrib.get("layout") == "@layout/status_card", (
    "status card must be the first normal dashboard content card"
)
included_layouts = [node.attrib.get("layout") for node in content if node.tag == "include"]
assert included_layouts.index("@layout/aurogon_protection_card") < included_layouts.index("@layout/night_protection_card"), (
    "Aurogon protection must appear before nighttime protection"
)
status_layout = ET.parse(ROOT / "app/src/main/res/layout/status_card.xml").getroot()
status_nodes = list(status_layout.iter())
status_ids = {node.attrib.get(android_id) for node in status_nodes}
for required in ("@+id/statusCard", "@+id/statusHeaderPanel", "@+id/statusDetailsPanel", "@+id/currentValuePanel"):
    assert required in status_ids, f"Missing status card control: {required}"
status_card = status_layout
assert status_card.tag == "com.rsps1008.fcmguard.CollapsingStatusCard", (
    "statusCard must keep its custom visual-bottom implementation"
)

details_panel = next(node for node in status_layout.iter() if node.attrib.get(android_id) == "@+id/statusDetailsPanel")
value_panel = next(node for node in status_layout.iter() if node.attrib.get(android_id) == "@+id/currentValuePanel")
assert details_panel.attrib.get(android_width) == "match_parent", (
    "statusDetailsPanel must span the same card width as the whitelist panel"
)
assert value_panel.attrib.get(android_width) == "match_parent", (
    "currentValuePanel must span the same card width as the detailed status cover"
)

print("Responsive layout checks passed for:")
for name, width in profiles:
    print(f"  - {name}: {width}dp")

# The night card must remain a vertical, wrapping part of the existing dashboard.
night = ET.parse(ROOT / "app/src/main/res/layout/night_protection_card.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
assert night.attrib[ns + "orientation"] == "vertical"
night_ids = {node.attrib.get(android_id) for node in night.iter()}
for required in ("nightSwitch", "nightSummary", "nightWarning", "nightExperimental",
                 "nightCleanup"):
    assert "@+id/" + required in night_ids, f"Missing night protection control: {required}"
for node in night.iter():
    if node.tag in ("Button", "Switch", "TextView"):
        assert node.attrib[android_width] == "match_parent", "Night controls must fit narrow cards"
        assert node.attrib[ns + "layout_height"] == "wrap_content", "Night text must wrap at large font scales"
        assert node.attrib.get(ns + "singleLine", "false") == "false"
print("Night protection card wrapping/control checks passed")

aurogon = ET.parse(ROOT / "app/src/main/res/layout/aurogon_protection_card.xml").getroot()
aurogon_ids = {node.attrib.get(android_id) for node in aurogon.iter()}
for required in ("aurogonSwitch", "aurogonExplanation", "aurogonSummary", "aurogonShizukuSetup",
                 "aurogonToolsRow", "aurogonClearSetting", "aurogonSyncNow", "aurogonViewSetting"):
    assert "@+id/" + required in aurogon_ids, f"Missing Aurogon control: {required}"
assert "@+id/nightSwitch" not in aurogon_ids, "Aurogon controls must be in a separate card"
aurogon_row = next(node for node in aurogon.iter() if node.attrib.get(android_id) == "@+id/aurogonToolsRow")
assert aurogon_row.attrib.get(ns + "orientation") == "horizontal"
settings = ET.parse(ROOT / "app/src/main/res/layout/dashboard_scroll_content.xml").getroot()
settings_ids = {node.attrib.get(android_id) for node in settings.iter()}
for required in ("protectionSettingsCard", "repairBtn", "fcmActionsRow", "wakeBtn", "diagBtn",
                 "protectionSwitch", "notificationSwitch", "permissionCard"):
    assert "@+id/" + required in settings_ids, f"Missing protection setting control: {required}"
settings_card = next(node for node in settings.iter() if node.attrib.get(android_id) == "@+id/protectionSettingsCard")
card_order = [node.attrib.get(android_id) for node in settings_card.iter()]
for before, after in (("protectionSwitch", "repairBtn"), ("repairBtn", "wakeBtn"),
                      ("diagBtn", "notificationSwitch")):
    assert card_order.index("@+id/" + before) < card_order.index("@+id/" + after), (
        f"{before} must appear before {after} in the protection settings card"
    )
action_row = next(node for node in settings_card.iter() if node.attrib.get(android_id) == "@+id/fcmActionsRow")
assert action_row.attrib.get(ns + "orientation") == "horizontal"
assert "@+id/aurogonClearSetting" not in settings_ids
assert "@+id/aurogonSyncNow" not in settings_ids
assert "@+id/aurogonViewSetting" not in settings_ids
print("Protection tools placement and Aurogon card checks passed")
