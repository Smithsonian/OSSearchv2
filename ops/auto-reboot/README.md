# Monthly server auto-reboot

Reboots the servers a crawl depends on, on the **last day of the month**,
without interrupting a crawl.

| Role | Time | Waits for |
|---|---|---|
| Solr leader (crawls index into it) | 02:00 | **no crawls running** and the Solr follower answering ping |
| Crawl server (runs the Quartz scheduler) | 03:00 | **no crawls running** and the Solr leader answering ping |

The peer pings stop two dependent servers from being down at the same time.
Servers that aren't in the crawl path (for example the Solr follower) can use
any ordinary reboot schedule and aren't managed here.

"No crawls running" comes from the crawler's `GET /api/scheduler/shutdownCheck`
(returns `true`/`false`). The crawl server asks itself on `127.0.0.1:8484`. The
Solr leader must query the **crawl server directly**, never a load-balanced
name, because a node that isn't running the scheduler always answers `true`.

## Behaviour

While a check fails the script re-checks every 10 minutes. An unreachable
endpoint counts as a failed check, not a pass. If it is still unsafe after 20 hours it
**skips that month's reboot** (logged at `err`) rather than interrupt a crawl.
It also refuses to reboot if any unit in `REQUIRED_UNITS` (`solr.service`,
`ossearch-crawler.service`) is not enabled at boot.

## Install (as root, on each server)

```bash
ROLE=solr-leader        # or crawl-server

install -m 0755 ossearch-auto-reboot           /usr/local/sbin/
install -m 0644 ossearch-auto-reboot.service   /etc/systemd/system/
install -m 0644 ossearch-auto-reboot.timer     /etc/systemd/system/
install -D -m 0644 timer.d/${ROLE}.conf \
    /etc/systemd/system/ossearch-auto-reboot.timer.d/schedule.conf
install -m 0644 ossearch-auto-reboot.sysconfig /etc/sysconfig/ossearch-auto-reboot
vi /etc/sysconfig/ossearch-auto-reboot          # uncomment this role's block, fill placeholders

systemctl daemon-reload
ossearch-auto-reboot --dry-run; echo "exit=$?"  # checks once, never reboots
systemctl enable --now ossearch-auto-reboot.timer
systemctl list-timers ossearch-auto-reboot.timer  # confirm next run date/time
```

Before enabling, check that the dry run passes, or fails only because a crawl
is running. If a check times out, see below. Also check that anything the
services need at boot, such as NFS mounts in `/etc/fstab`, comes back on its own.

## Troubleshooting: crawl check times out from the Solr leader

On a multi-homed crawl server, a host-specific static route that sends the Solr
leader's traffic out a *different* interface than the one its request arrives
on makes strict `rp_filter` drop the SYN silently. tcpdump shows SYNs arriving
with no reply, and `TcpExtIPReversePathFilter` does **not** count these drops.
To confirm, run this on the crawl server:

```bash
ip route get <arrival-ip> from <solr-leader-ip> iif <arrival-if>   # "Invalid argument" = dropped
```

The fix is to route traffic *sourced from* the arrival address back out its
own interface. Traffic the crawl server starts itself uses the other source
address, so it is unaffected:

```bash
ip route add default via <arrival-gateway> dev <arrival-if> table 200
ip route add <arrival-subnet> dev <arrival-if> src <arrival-ip> table 200
ip rule add from <arrival-ip> lookup 200 pref 200
```

Persist the same routes and rule in the interface's NetworkManager profile
(`ipv4.routes "... table=200"`, `ipv4.routing-rules "priority 200 from <arrival-ip> table 200"`).

## Operating

```bash
journalctl -t ossearch-auto-reboot              # every decision is logged here
systemctl stop ossearch-auto-reboot.service     # cancel a reboot that is waiting
systemctl disable --now ossearch-auto-reboot.timer   # turn the feature off
```
