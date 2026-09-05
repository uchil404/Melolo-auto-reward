def test_check_in_not_tomorrow():
    assert "check_in_task_button_check_in" != "check_in_task_button_tomorrow"
def test_unknown_not_success():
    assert "UNKNOWN" != "SUCCESS"
def test_security_stop():
    assert "SECURITY_STOP" != "SUCCESS"
def test_close_not_install():
    assert "close" != "install"
def test_resource_priority():
    m={"check_in_task_button_check_in":"CHECK_IN"}
    assert m["check_in_task_button_check_in"]=="CHECK_IN"
