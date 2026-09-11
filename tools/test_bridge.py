import sys

sys.path.insert(0, r"D:\Tools\AzurPilot")
sys.stdout.reconfigure(encoding="utf-8")

import mobile_bridge as b

t = b.query_task_tree()
print("=== task_tree ===")
print("groups:", len(t["groups"]), "tasks:", t["count"])
for g in t["groups"]:
    names = ", ".join(x["name"] for x in g["tasks"][:5])
    print("  [{}] {} ({} 项) -> {}".format(g["key"], g["name"], len(g["tasks"]), names))

print()
print("=== meow_stats ===")
m = b.query_meow_stats("alas")
print("available:", m["available"], "rows:", len(m["rows"]))
if m["rows"]:
    print("  first:", m["rows"][0])

print()
print("=== ship_exp ===")
s = b.query_ship_exp("alas")
print("available:", s["available"], "target:", s["targetLevel"], "lastCheck:", s["lastCheckTime"])
print("avgBattle:", s["avgBattleSeconds"], "avgRound:", s["avgRoundSeconds"], "avgMeow:", s["avgMeowBattleSeconds"])
for sh in s["ships"][:3]:
    print("  ship:", sh)
print("daily days:", len(s["daily"]))
print("today:", s["today"])
