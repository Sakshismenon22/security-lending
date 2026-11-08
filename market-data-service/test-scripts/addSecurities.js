import axios from 'axios';

const securities = [
  { securityName: "Reliance Industries", currency: "INR", ISIN: "INE002A01018", symbol: "RELIANCE", exchange: "NSE" },
  { securityName: "Bharti Airtel", currency: "INR", ISIN: "INE397D01024", symbol: "BHARTIARTL", exchange: "NSE" },
  { securityName: "HDFC Bank", currency: "INR", ISIN: "INE040A01034", symbol: "HDFCBANK", exchange: "NSE" },
  { securityName: "ICICI Bank", currency: "INR", ISIN: "INE090A01021", symbol: "ICICIBANK", exchange: "NSE" },
  { securityName: "State Bank of India", currency: "INR", ISIN: "INE062A01020", symbol: "SBIN", exchange: "NSE" },
  { securityName: "Tata Consultancy Services", currency: "INR", ISIN: "INE467B01029", symbol: "TCS", exchange: "NSE" },
  { securityName: "Bajaj Finance", currency: "INR", ISIN: "INE296A01032", symbol: "BAJFINANCE", exchange: "NSE" },
  { securityName: "Larsen and Toubro", currency: "INR", ISIN: "INE018A01030", symbol: "LT", exchange: "NSE" },
  { securityName: "Life Insurance Corporation of India", currency: "INR", ISIN: "INE0J1Y01017", symbol: "LICI", exchange: "NSE" },
  { securityName: "Hindustan Unilever", currency: "INR", ISIN: "INE030A01027", symbol: "HINDUNILVR", exchange: "NSE" },
  { securityName: "Infosys", currency: "INR", ISIN: "INE009A01021", symbol: "INFY", exchange: "NSE" },
  { securityName: "Kotak Mahindra Bank", currency: "INR", ISIN: "INE237A01028", symbol: "KOTAKBANK", exchange: "NSE" },
  { securityName: "Adani Ports", currency: "INR", ISIN: "INE742F01042", symbol: "ADANIPORTS", exchange: "NSE" },
  { securityName: "Sun Pharmaceutical Industries", currency: "INR", ISIN: "INE044A01036", symbol: "SUNPHARMA", exchange: "NSE" },
  { securityName: "Titan Company", currency: "INR", ISIN: "INE280A01028", symbol: "TITAN", exchange: "NSE" },
  { securityName: "Adani Power", currency: "INR", ISIN: "INE814H01029", symbol: "ADANIPOWER", exchange: "NSE" },
  { securityName: "Axis Bank", currency: "INR", ISIN: "INE238A01034", symbol: "AXISBANK", exchange: "NSE" },
  { securityName: "HCL Technologies", currency: "INR", ISIN: "INE860A01027", symbol: "HCLTECH", exchange: "NSE" },
  { securityName: "Maruti Suzuki India", currency: "INR", ISIN: "INE585B01010", symbol: "MARUTI", exchange: "NSE" },
  { securityName: "Mahindra and Mahindra", currency: "INR", ISIN: "INE101A01026", symbol: "M&M", exchange: "NSE" },
  { securityName: "Adani Enterprises", currency: "INR", ISIN: "INE423A01024", symbol: "ADANIENT", exchange: "NSE" },
  { securityName: "ITC", currency: "INR", ISIN: "INE154A01025", symbol: "ITC", exchange: "NSE" },
  { securityName: "UltraTech Cement", currency: "INR", ISIN: "INE481G01011", symbol: "ULTRACEMCO", exchange: "NSE" },
  { securityName: "Hindustan Aeronautics", currency: "INR", ISIN: "INE066F01020", symbol: "HAL", exchange: "NSE" },
  { securityName: "Eternal", currency: "INR", ISIN: "INE758T01015", symbol: "ETERNAL", exchange: "NSE" },
  { securityName: "JSW Steel", currency: "INR", ISIN: "INE019A01038", symbol: "JSWSTEEL", exchange: "NSE" },
  { securityName: "NTPC", currency: "INR", ISIN: "INE733E01010", symbol: "NTPC", exchange: "NSE" },
  { securityName: "Oil and Natural Gas Corporation", currency: "INR", ISIN: "INE213A01029", symbol: "ONGC", exchange: "NSE" },
  { securityName: "Bajaj Auto", currency: "INR", ISIN: "INE917I01010", symbol: "BAJAJ-AUTO", exchange: "NSE" },
  { securityName: "Bharat Electronics", currency: "INR", ISIN: "INE263A01024", symbol: "BEL", exchange: "NSE" },
  { securityName: "Bajaj Finserv", currency: "INR", ISIN: "INE918I01026", symbol: "BAJAJFINSV", exchange: "NSE" },
  { securityName: "Divi's Laboratories", currency: "INR", ISIN: "INE361B01024", symbol: "DIVISLAB", exchange: "NSE" },
  { securityName: "Coal India", currency: "INR", ISIN: "INE522F01014", symbol: "COALINDIA", exchange: "NSE" },
  { securityName: "Power Grid Corporation of India", currency: "INR", ISIN: "INE752E01010", symbol: "POWERGRID", exchange: "NSE" },
  { securityName: "Nestle India", currency: "INR", ISIN: "INE239A01024", symbol: "NESTLEIND", exchange: "NSE" },
  { securityName: "Hindustan Zinc", currency: "INR", ISIN: "INE267A01025", symbol: "HINDZINC", exchange: "NSE" },
  { securityName: "Asian Paints", currency: "INR", ISIN: "INE021A01026", symbol: "ASIANPAINT", exchange: "NSE" },
  { securityName: "Adani Green Energy", currency: "INR", ISIN: "INE364U01010", symbol: "ADANIGREEN", exchange: "NSE" },
  { securityName: "Tata Steel", currency: "INR", ISIN: "INE081A01020", symbol: "TATASTEEL", exchange: "NSE" },
  { securityName: "Avenue Supermarts", currency: "INR", ISIN: "INE192R01011", symbol: "DMART", exchange: "NSE" },
  { securityName: "Shriram Finance", currency: "INR", ISIN: "INE721A01047", symbol: "SHRIRAMFIN", exchange: "NSE" },
  { securityName: "Grasim Industries", currency: "INR", ISIN: "INE047A01021", symbol: "GRASIM", exchange: "NSE" },
  { securityName: "Hindalco Industries", currency: "INR", ISIN: "INE038A01020", symbol: "HINDALCO", exchange: "NSE" },
  { securityName: "TVS Motor Company", currency: "INR", ISIN: "INE494B01023", symbol: "TVSMOTOR", exchange: "NSE" },
  { securityName: "Eicher Motors", currency: "INR", ISIN: "INE066A01021", symbol: "EICHERMOT", exchange: "NSE" },
  { securityName: "Indian Oil Corporation", currency: "INR", ISIN: "INE242A01010", symbol: "IOC", exchange: "NSE" },
  { securityName: "InterGlobe Aviation", currency: "INR", ISIN: "INE646L01027", symbol: "INDIGO", exchange: "NSE" },
  { securityName: "Wipro", currency: "INR", ISIN: "INE075A01022", symbol: "WIPRO", exchange: "NSE" },
  { securityName: "Torrent Pharmaceuticals", currency: "INR", ISIN: "INE685A01028", symbol: "TORNTPHARM", exchange: "NSE" },
  { securityName: "DLF", currency: "INR", ISIN: "INE271C01023", symbol: "DLF", exchange: "NSE" }
];

async function addSecurity(security){
    const res = await axios.post('http://localhost:8080/api/v1/security-master/add-security', security);

    console.log(res);
}

for(let security of securities){
   await addSecurity(security);
}