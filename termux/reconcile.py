def reconcile():
    from status_v2 import check_installation, check_accessibility, check_service_liveness
    inst=check_installation(); acc=check_accessibility(); alive,last=check_service_liveness()
    return {"installation":"installed" if inst else "missing","accessibility":"enabled" if acc else "disabled","service":"alive" if alive else "dead","last_seen":last}
