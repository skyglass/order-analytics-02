import streamlit as st
import pandas as pd
from pinotdb import connect
from datetime import datetime
import time
import plotly.express as px
import plotly.graph_objects as go 
import os
import requests

pinot_host=os.environ.get("PINOT_SERVER", "pinot-broker")
pinot_port=os.environ.get("PINOT_PORT", 8099)
conn = connect(pinot_host, pinot_port)

delivery_service_api = "http://kafka-streams-quarkus:8080"

st.set_page_config(layout="wide")
st.title("All About That Dough Dashboard 🍕")


now = datetime.now()
dt_string = now.strftime("%d %B %Y %H:%M:%S")
st.write(f"Last update: {dt_string}")

# Use session state to keep track of whether we need to auto refresh the page and the refresh frequency

if not "sleep_time" in st.session_state:
    st.session_state.sleep_time = 2

if not "auto_refresh" in st.session_state:
    st.session_state.auto_refresh = True

auto_refresh = st.checkbox('Auto Refresh?', st.session_state.auto_refresh)

if auto_refresh:
    number = st.number_input('Refresh rate in seconds', value=st.session_state.sleep_time)
    st.session_state.sleep_time = number

curs = conn.cursor()

pinot_available = False
try:
    curs.execute("select * FROM orders where ts > ago('PT2M')")
    if not curs.description:
        st.warning("Connected to Pinot, but no orders imported",icon="⚠️")    

    pinot_available = curs.description is not None
except Exception as e:
    st.warning(f"""Unable to connect to or query Apache Pinot [{pinot_host}:{pinot_port}] 

Exception: {e}""",icon="⚠️")

if pinot_available:
    response = requests.get(f"{delivery_service_api}/orders/overview2").json()
    # st.write(response)

    current_time_period = response["currentTimePeriod"]
    previous_time_period = response["previousTimePeriod"]

    metric1, metric2, metric3 = st.columns(3)

    metric1.metric(
        label="# of Orders",
        value="{:,}".format(current_time_period["orders"]),
        delta="{:,}".format(int(current_time_period["orders"] - previous_time_period["orders"])) 
            if previous_time_period["orders"] > 0 else None
    )

    metric2.metric(
        label="# of Orders",
        value="{:,}".format(current_time_period["totalPrice"]),
        delta="{:,}".format(int(current_time_period["totalPrice"] - previous_time_period["totalPrice"])) 
            if previous_time_period["totalPrice"] > 0 else None
    )

    average_order_value_1min = current_time_period["totalPrice"] / int(current_time_period["orders"])
    average_order_value_1min_2min = (previous_time_period["totalPrice"] / int(previous_time_period["orders"])
                                     if int(previous_time_period["orders"]) > 0
                                     else 0)

    metric3.metric(
        label="Average order value in ₹",
        value="{:,.2f}".format(average_order_value_1min),
        delta="{:,.2f}".format(average_order_value_1min - average_order_value_1min_2min) 
            if average_order_value_1min_2min > 0 else None
    )

    response = requests.get(f"{delivery_service_api}/orders/ordersPerMinute").json()
    df_ts = pd.DataFrame(response)

    if df_ts.shape[0] > 1:
        df_ts_melt = pd.melt(df_ts, id_vars=['timestamp'], value_vars=['revenue', 'orders'])

        col1, col2 = st.columns(2)
        with col1:
            orders = df_ts_melt[df_ts_melt.variable == "orders"]
            latest_date = orders.timestamp.max()
            latest_date_but_one = (orders.sort_values(by=["timestamp"], ascending=False)
                                   .iloc[[1]].timestamp.values[0])

            revenue_complete = orders[orders.timestamp < latest_date]
            revenue_incomplete = orders[orders.timestamp >= latest_date_but_one]

            fig = go.FigureWidget(data=[
                go.Scatter(x=revenue_complete.timestamp,
                           y=revenue_complete.value, mode='lines',
                           line={'dash': 'solid', 'color': 'green'}),
                go.Scatter(x=revenue_incomplete.timestamp,
                           y=revenue_incomplete.value, mode='lines',
                           line={'dash': 'dash', 'color': 'green'}),
            ])
            fig.update_layout(showlegend=False, title="Orders per minute",
                              margin=dict(l=0, r=0, t=40, b=0),)
            fig.update_yaxes(range=[0, df_ts["orders"].max() * 1.1])
            st.plotly_chart(fig, use_container_width=True)

        with col2:
            revenue = df_ts_melt[df_ts_melt.variable == "revenue"]
            latest_date = revenue.timestamp.max()
            latest_date_but_one = (revenue.sort_values(by=["timestamp"], ascending=False)
                                   .iloc[[1]].timestamp.values[0])

            revenue_complete = revenue[revenue.timestamp < latest_date]
            revenue_incomplete = revenue[revenue.timestamp >=
                                         latest_date_but_one]

            fig = go.FigureWidget(data=[
                go.Scatter(x=revenue_complete.timestamp,
                           y=revenue_complete.value, mode='lines',
                           line={'dash': 'solid', 'color': 'blue'}),
                go.Scatter(x=revenue_incomplete.timestamp,
                           y=revenue_incomplete.value, mode='lines',
                           line={'dash': 'dash', 'color': 'blue'}),
            ])
            fig.update_layout(showlegend=False, title="Revenue per minute",
                              margin=dict(l=0, r=0, t=40, b=0),)
            fig.update_yaxes(range=[0, df_ts["revenue"].max() * 1.1])
            st.plotly_chart(fig, use_container_width=True)

    response = requests.get(f"{delivery_service_api}/orders/latestOrders").json()
    df = pd.DataFrame(response)

    st.subheader("Latest Orders")

    # CSS to inject contained in a string
    hide_table_row_index = """
                <style>
                thead tr th:first-child {display:none}
                tbody th {display:none}
                </style>
                """

    # Inject CSS with Markdown
    st.markdown(hide_table_row_index, unsafe_allow_html=True)

    st.markdown(
        df.to_html(),
        unsafe_allow_html=True
    )

    curs.close()

if auto_refresh:
    time.sleep(number)
    st.experimental_rerun()