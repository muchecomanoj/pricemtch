// Centralized dummy JSON. Mirrors the JSON shapes the Java backend will return.
import { ROLES } from '../constants'

export const mockUser = {
  id: 1,
  name: 'Manoj Maharana',
  email: 'admin@priceintel.com',
  role: ROLES.ADMIN,
  roles: [ROLES.ADMIN],
  accessType: 'ADMIN',
  avatar: null,
}

export const mockToken = 'mock.jwt.token'

export const products = Array.from({ length: 47 }).map((_, i) => ({
  id: i + 1,
  sku: `SKU-${1000 + i}`,
  asin: `B0${(8 + (i % 9))}N5WR${100 + i}`,
  title: [
    'Echo Dot (5th Gen)', 'Fire TV Stick 4K', 'Kindle Paperwhite',
    'Sony WH-1000XM4', 'Logitech MX Master 3', 'Anker PowerCore 20000',
  ][i % 6] + ` #${i + 1}`,
  brand: ['Amazon', 'Sony', 'Logitech', 'Anker'][i % 4],
  category: ['Electronics', 'Home', 'Accessories', 'Audio'][i % 4],
  price: +(29 + (i % 20) + 0.99).toFixed(2),
  margin: +(15 + (i % 25)).toFixed(1),
  status: ['Matched', 'Review', 'Unmatched'][i % 3],
  marketplaces: ['Amazon', 'eBay'].slice(0, (i % 2) + 1),
  image: `https://placehold.co/80x80?text=P${i + 1}`,
}))

export const competitorListings = Array.from({ length: 18 }).map((_, i) => ({
  id: i + 1,
  marketplace: ['Amazon', 'eBay', 'Walmart', 'Web'][i % 4],
  seller: ['SellerX', 'PrimeGoods', 'MegaShop', 'ValueMart'][i % 4],
  price: +(38 + (i % 15) + 0.99).toFixed(2),
  shipping: i % 3 === 0 ? 0 : +(3.99 + (i % 3)).toFixed(2),
  availability: ['In Stock', 'Low Stock', 'Out of Stock'][i % 3],
  rating: +(3.8 + (i % 12) / 10).toFixed(1),
  condition: ['New', 'Used', 'Refurbished'][i % 3],
  updatedAt: `${(i % 12) + 1}h ago`,
}))

export const matchCandidates = Array.from({ length: 6 }).map((_, i) => ({
  id: i + 1,
  title: ['Echo Dot (5th Gen) Charcoal', 'Echo Dot 5 Gen - Blue', 'Amazon Echo Dot 2022'][i % 3],
  marketplace: ['Amazon', 'eBay', 'Walmart'][i % 3],
  confidence: +(0.6 + (i % 4) / 10).toFixed(2),
  image: `https://placehold.co/120x120?text=M${i + 1}`,
  reasons: ['ASIN exact match', 'Brand + model match', 'Image similarity 0.88'],
  conflicts: i % 2 === 0 ? [] : ['Pack size differs', 'Condition: Used'],
}))

export const priceIntel = {
  current: 44.99, lowest: 39.99, highest: 54.99, median: 46.5, average: 47.1,
  history: {
    labels: ['W1', 'W2', 'W3', 'W4', 'W5', 'W6', 'W7', 'W8'],
    ours: [45, 45, 44, 44, 45, 45, 44, 45],
    lowest: [42, 41, 40, 40, 39, 40, 39, 40],
    highest: [52, 53, 54, 53, 55, 54, 55, 55],
  },
  byMarketplace: { labels: ['Amazon', 'eBay', 'Walmart', 'Web'], values: [44.99, 42.5, 46.0, 48.99] },
}

export const costModel = {
  cogs: 18.0, shipping: 3.5, fees: 6.75, advertising: 2.4, returns: 1.1, storage: 0.85, taxes: 0,
}

export const profitability = {
  roi: 62.4, margin: 27.4, netProfit: 12.2, grossProfit: 19.5, breakEven: 32.6,
  chart: { labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'], net: [9, 10, 11, 12, 12.5, 12.2] },
}

export const recommendations = Array.from({ length: 8 }).map((_, i) => ({
  id: i + 1,
  product: ['Echo Dot (5th Gen)', 'Fire TV Stick 4K', 'Kindle Paperwhite'][i % 3],
  recommendedPrice: +(39 + (i % 10) + 0.99).toFixed(2),
  currentPrice: +(42 + (i % 8) + 0.99).toFixed(2),
  confidence: +(0.7 + (i % 3) / 10).toFixed(2),
  risk: ['Low', 'Medium', 'High'][i % 3],
  reason: 'Aligns with median competitor landed price while protecting target margin.',
  status: 'Pending',
}))

export const alerts = Array.from({ length: 14 }).map((_, i) => ({
  id: i + 1,
  type: ['Price Drop', 'Out of Stock', 'New Seller', 'Margin Alert', 'Search Alert'][i % 5],
  product: ['Echo Dot (5th Gen)', 'Fire TV Stick 4K', 'Kindle Paperwhite'][i % 3],
  severity: ['danger', 'warning', 'info', 'success'][i % 4],
  message: 'Competitor price moved beyond configured threshold.',
  time: `${(i % 24) + 1}h ago`,
  read: i % 3 === 0,
}))

export const users = Array.from({ length: 9 }).map((_, i) => {
  const accessType = [ROLES.ADMIN, ROLES.MANAGER, ROLES.ANALYST, ROLES.VIEWER][i % 4]
  return {
    id: i + 1,
    firstName: ['Manoj', 'Anita', 'David', 'Sara'][i % 4],
    lastName: ['Maharana', 'Rao', 'Kim', 'Lee'][i % 4],
    email: `user${i + 1}@priceintel.com`,
    accessType,
    role: accessType,
    status: i % 4 !== 3 ? 'ACTIVE' : 'INACTIVE',
  }
})

export const aiSuggestedQuestions = [
  'Which products have the lowest margin this week?',
  'Summarize competitor price changes for Echo Dot.',
  'What price should I set for Fire TV Stick 4K?',
  'Show products at risk of margin breach.',
]
